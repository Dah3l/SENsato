package com.apagones.habana.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.apagones.habana.R
import com.apagones.habana.data.AppNotification
import com.apagones.habana.data.SettingsRepository
import com.apagones.habana.notification.NotificationHelper
import com.apagones.habana.parser.MentionMatcher
import com.apagones.habana.parser.SupabasePostSource
import com.apagones.habana.parser.TelegramChannelParser
import com.apagones.habana.parser.TelegramPost

/**
 * Worker que se ejecuta periódicamente (ver [MonitoringScheduler]).
 * Promovido a Foreground Service para garantizar ejecución constante en segundo plano
 * incluso si la aplicación está cerrada o quitada de aplicaciones recientes.
 */
class MonitoringWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val repo = SettingsRepository(applicationContext)
        val settings = repo.readOnce()
        val notification = NotificationHelper.buildStatusNotification(applicationContext, settings)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationHelper.NOTIF_ID_STATUS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NotificationHelper.NOTIF_ID_STATUS, notification)
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val repo = SettingsRepository(context)
        val supabaseSource = SupabasePostSource()
        val telegramParser = TelegramChannelParser()

        return try {
            val settings = repo.readOnce()

            // Si el usuario pausó el monitoreo, no hacer nada
            if (settings.paused) return Result.success()

            // Promover a Foreground Service para máxima persistencia en segundo plano
            try {
                setForeground(getForegroundInfo())
            } catch (_: Exception) {
                // Ignorar si el sistema restringe primer plano bajo ciertas condiciones
            }

            val lastSeen = settings.lastSeenPostId

            // 1. Detectar si hay circuitos monitoreados que siguen 'En espera' (sin estado conocido)
            val unknownCircuits = settings.circuits.filter { !settings.knownCircuits.contains(it) }

            // 2. BACKFILL SILENCIOSO: Si es la 1ª ejecución (lastSeen == 0L) o hay circuitos en espera,
            // procesamos publicaciones históricas para actualizar estados SIN mover lastSeenPostId (salvo en el 1º inicio absoluto).
            if (lastSeen == 0L || unknownCircuits.isNotEmpty()) {
                val historicalPosts: List<TelegramPost> = try {
                    supabaseSource.fetchPosts(sinceId = 0L)
                } catch (_: Exception) {
                    telegramParser.fetchPosts()
                }

                if (historicalPosts.isNotEmpty()) {
                    // Procesar mensajes históricamente para resolver estados e historial sin alertas flotantes
                    for (post in historicalPosts) {
                        notifyForPost(
                            post = post,
                            circuits = settings.circuits,
                            notifyNational = false,
                            context = context,
                            repo = repo,
                            sendPushNotification = false
                        )
                    }

                    // En la 1ª ejecución absoluta de la app, inicializamos el watermark con el post más reciente visto
                    if (lastSeen == 0L) {
                        repo.setLastSeenPostId(historicalPosts.maxOf { it.idPost })
                        repo.setLastCheck(System.currentTimeMillis())
                        NotificationHelper.updateStatusNotification(context, repo.readOnce())
                        MonitoringScheduler.scheduleNext(context)
                        return Result.success()
                    }
                    // NOTA: Si lastSeen > 0L (backfill por nuevo circuito), NO se toca lastSeenPostId aquí.
                }
            }

            // 3. FETCH NORMAL DE POSTS NUEVOS (id > lastSeen)
            val posts: List<TelegramPost> = try {
                supabaseSource.fetchPosts(sinceId = lastSeen)
            } catch (_: Exception) {
                telegramParser.fetchPosts()
            }

            val newPosts = posts.filter { it.idPost > lastSeen }

            if (newPosts.isNotEmpty()) {
                // Notificar en tiempo real únicamente los posts realmente nuevos
                for (post in newPosts) {
                    notifyForPost(
                        post = post,
                        circuits = settings.circuits,
                        notifyNational = settings.notifyNationalReport,
                        context = context,
                        repo = repo,
                        sendPushNotification = true
                    )
                }

                // El watermark lastSeenPostId SOLO se avanza con la llegada de posts nuevos verdaderos
                repo.setLastSeenPostId(newPosts.maxOf { it.idPost })
            }

            // 4. Registrar la revisión exitosa y programar el próximo escaneo
            repo.setLastCheck(System.currentTimeMillis())
            NotificationHelper.updateStatusNotification(context, repo.readOnce())
            MonitoringScheduler.scheduleNext(context)

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    /** Publica las notificaciones correspondientes a un post nuevo, actualiza el estado y guarda en el historial. */
    private suspend fun notifyForPost(
        post: TelegramPost,
        circuits: List<String>,
        notifyNational: Boolean,
        context: Context,
        repo: SettingsRepository,
        sendPushNotification: Boolean = true
    ) {
        // a) Menciones exactas de circuitos del usuario
        val matched = MentionMatcher.findMatchingCircuits(post.text, circuits)
        val circuitStatuses = if (matched.isNotEmpty()) {
            repo.markCircuitsKnown(matched)

            // Análisis avanzado por proximidad de párrafos (soporta posts con múltiples estados)
            val statuses = MentionMatcher.detectCircuitStatuses(post.text, matched)
            val affectedList = statuses.filter { it.value == MentionMatcher.StatusType.AFFECTED }.keys.toList()
            val restoredList = statuses.filter { it.value == MentionMatcher.StatusType.RESTORED }.keys.toList()

            if (affectedList.isNotEmpty()) {
                repo.setCircuitsAffected(affectedList, true, post.timestamp)
            }
            if (restoredList.isNotEmpty()) {
                repo.setCircuitsAffected(restoredList, false, post.timestamp)
            }
            statuses
        } else {
            emptyMap()
        }

        for ((index, circuit) in matched.withIndex()) {
            val statusType = circuitStatuses[circuit.uppercase()]
            // Generar título descriptivo según el estado detectado para ese circuito en particular
            val title = when (statusType) {
                MentionMatcher.StatusType.AFFECTED -> context.getString(R.string.notif_title_affected, circuit)
                MentionMatcher.StatusType.RESTORED -> context.getString(R.string.notif_title_restored, circuit)
                else -> context.getString(R.string.notif_title_circuit, circuit)
            }

            if (sendPushNotification) {
                NotificationHelper.showNotification(
                    context = context,
                    title = title,
                    text = post.text,
                    postUrl = post.urlPost,
                    notifId = (post.idPost % 100_000).toInt() * 10 + index,
                    timestamp = post.timestamp
                )
            }

            repo.addNotification(
                AppNotification(
                    id = post.idPost * 10 + index,
                    title = title,
                    text = post.text,
                    postUrl = post.urlPost,
                    timestamp = post.timestamp
                )
            )
        }

        // b) Parte nacional del SEN (si el usuario activó la opción)
        if (notifyNational && matched.isEmpty() && MentionMatcher.isNationalReport(post.text)) {
            val title = context.getString(R.string.notif_title_national)
            if (sendPushNotification) {
                NotificationHelper.showNotification(
                    context = context,
                    title = title,
                    text = post.text,
                    postUrl = post.urlPost,
                    notifId = (post.idPost % 100_000).toInt() * 10 + 9,
                    timestamp = post.timestamp
                )
            }
            repo.addNotification(
                AppNotification(
                    id = post.idPost * 10 + 9,
                    title = title,
                    text = post.text,
                    postUrl = post.urlPost,
                    timestamp = post.timestamp
                )
            )
        }
    }
}
