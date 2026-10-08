package com.apagones.habana.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apagones.habana.R
import com.apagones.habana.data.AppNotification
import com.apagones.habana.data.SettingsRepository
import com.apagones.habana.notification.NotificationHelper
import com.apagones.habana.parser.MentionMatcher
import com.apagones.habana.parser.TelegramChannelParser
import com.apagones.habana.parser.TelegramPost

/**
 * Worker que se ejecuta periódicamente (ver [MonitoringScheduler]).
 *
 * Flujo:
 *  1. Lee del DataStore los circuitos y el último id de post visto.
 *  2. Descarga los posts del canal público con Jsoup ([TelegramChannelParser]).
 *  3. Procesa SOLO los posts con id > último visto:
 *       - si mencionan un circuito del usuario -> notificación local e historial,
 *       - si indican afectación o restablecimiento por proximidad de párrafos -> actualiza el estado visible,
 *       - si contienen "Situación del SEN" y la opción está activa -> notificación e historial.
 *  4. Actualiza el último id visto y la hora de última revisión.
 *
 * En la PRIMERA ejecución no notifica el histórico: solo marca como visto
 * el post más reciente para evitar una avalancha de avisos antiguos.
 */
class MonitoringWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val repo = SettingsRepository(context)
        val parser = TelegramChannelParser()

        return try {
            val settings = repo.readOnce()

            // Si el usuario pausó el monitoreo, no hacer nada (se reprograma
            // automáticamente cuando vuelva a activarlo desde la UI).
            if (settings.paused) return Result.success()

            // 2. Descargar posts (lanza IOException si no hay red -> retry)
            val posts = parser.fetchPosts()
            if (posts.isEmpty()) return Result.retry()

            val lastSeen = settings.lastSeenPostId

            // 1ª ejecución: sin estado previo -> no spam del histórico
            if (lastSeen == 0L) {
                repo.setLastSeenPostId(posts.maxOf { it.idPost })
                repo.setLastCheck(System.currentTimeMillis())
                NotificationHelper.updateStatusNotification(context, repo.readOnce())
                MonitoringScheduler.scheduleNext(context)
                return Result.success()
            }

            // 3. Posts nuevos = id mayor que el último visto
            val newPosts = posts.filter { it.idPost > lastSeen }

            for (post in newPosts) {
                notifyForPost(post, settings.circuits, settings.notifyNationalReport, context, repo)
            }

            // 4. Avanzar el watermark y registrar la revisión
            repo.setLastSeenPostId(posts.maxOf { it.idPost })
            repo.setLastCheck(System.currentTimeMillis())
            NotificationHelper.updateStatusNotification(context, repo.readOnce())
            MonitoringScheduler.scheduleNext(context)

            Result.success()
        } catch (e: Exception) {
            // Errores de red o de parseo: reintentar en la siguiente ventana
            Result.retry()
        }
    }

    /** Publica las notificaciones correspondientes a un post nuevo, actualiza el estado y guarda en el historial. */
    private suspend fun notifyForPost(
        post: TelegramPost,
        circuits: List<String>,
        notifyNational: Boolean,
        context: Context,
        repo: SettingsRepository
    ) {
        // a) Menciones exactas de circuitos del usuario
        val matched = MentionMatcher.findMatchingCircuits(post.text, circuits)
        if (matched.isNotEmpty()) {
            repo.markCircuitsKnown(matched)

            // Análisis avanzado por proximidad de párrafos (soporta posts con múltiples estados)
            val circuitStatuses = MentionMatcher.detectCircuitStatuses(post.text, matched)
            val affectedList = circuitStatuses.filter { it.value == MentionMatcher.StatusType.AFFECTED }.keys.toList()
            val restoredList = circuitStatuses.filter { it.value == MentionMatcher.StatusType.RESTORED }.keys.toList()

            if (affectedList.isNotEmpty()) {
                repo.setCircuitsAffected(affectedList, true, post.timestamp)
            }
            if (restoredList.isNotEmpty()) {
                repo.setCircuitsAffected(restoredList, false, post.timestamp)
            }
        }

        for ((index, circuit) in matched.withIndex()) {
            val title = context.getString(R.string.notif_title_circuit, circuit)
            NotificationHelper.showNotification(
                context = context,
                title = title,
                text = post.text,
                postUrl = post.urlPost,
                // Id único por post y circuito (evita que dos avisos se pisen)
                notifId = (post.idPost % 100_000).toInt() * 10 + index,
                timestamp = post.timestamp
            )
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
            NotificationHelper.showNotification(
                context = context,
                title = title,
                text = post.text,
                postUrl = post.urlPost,
                notifId = (post.idPost % 100_000).toInt() * 10 + 9,
                timestamp = post.timestamp
            )
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
