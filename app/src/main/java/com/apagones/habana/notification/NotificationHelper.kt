package com.apagones.habana.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.apagones.habana.MainActivity
import com.apagones.habana.R
import com.apagones.habana.data.AppSettings
import com.apagones.habana.receiver.NotificationActionReceiver
import java.text.DateFormat
import java.util.Date

/**
 * Helper de notificaciones locales.
 * Crea los canales "Apagones" (alta prioridad para alertas) y "Estado del monitoreo"
 * (prioridad baja para la notificación permanente con cuenta regresiva).
 */
object NotificationHelper {

    /** Id del canal de alertas (nombre visible: "Apagones"). */
    const val CHANNEL_ID = "apagones_channel"

    /** Id del canal de estado del servicio (nombre visible: "Estado del monitoreo"). */
    const val STATUS_CHANNEL_ID = "apagones_status_channel"

    /** Prefijo para generar ids únicos por post/circuito. */
    private const val NOTIF_ID_BASE = 4000

    /** Id fijo para la notificación permanente de estado. */
    const val NOTIF_ID_STATUS = 1001

    /** Request codes para PendingIntents del estado. */
    private const val REQ_OPEN_APP = 2001
    private const val REQ_ACTION_PAUSE = 2002
    private const val REQ_ACTION_RESUME = 2003

    /**
     * Crea los canales de notificaciones. Seguro llamarlo varias veces:
     * si ya existen, Android lo ignora. Llamar desde Application.onCreate().
     */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // 1. Canal de alertas de apagones (alta prioridad)
        val alertChannel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
        }

        // 2. Canal de estado permanente (prioridad baja: no vibra ni suena)
        val statusChannel = NotificationChannel(
            STATUS_CHANNEL_ID,
            context.getString(R.string.status_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.status_channel_desc)
            setShowBadge(false)
        }

        manager.createNotificationChannel(alertChannel)
        manager.createNotificationChannel(statusChannel)
    }

    /** true si podemos publicar notificaciones (en API<33 siempre true). */
    fun havePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Publica una notificación local con el texto del aviso y el timestamp del mensaje de Telegram.
     */
    fun showNotification(
        context: Context,
        title: String,
        text: String,
        postUrl: String,
        notifId: Int,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (!havePermission(context)) return

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", 1) // Abrir la aplicación Mi Circuito en la pestaña de Historial
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(timestamp)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIF_ID_BASE + notifId, notification)
    }

    /**
     * Construye el objeto Notification para el estado permanente del servicio.
     */
    fun buildStatusNotification(context: Context, settings: AppSettings): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            REQ_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (settings.paused) {
            val resumeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_RESUME
            }
            val resumePendingIntent = PendingIntent.getBroadcast(
                context,
                REQ_ACTION_RESUME,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.setContentTitle(context.getString(R.string.notif_status_paused_title))
                .setContentText(context.getString(R.string.notif_status_paused_text))
                .setUsesChronometer(false)
                .addAction(
                    android.R.drawable.ic_media_play,
                    context.getString(R.string.action_resume),
                    resumePendingIntent
                )
        } else {
            val pauseIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_PAUSE
            }
            val pausePendingIntent = PendingIntent.getBroadcast(
                context,
                REQ_ACTION_PAUSE,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val lastCheckStr = if (settings.lastCheckMillis > 0) {
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(settings.lastCheckMillis))
            } else {
                context.getString(R.string.notif_status_never_scanned)
            }

            val intervalMillis = 2 * 60 * 1000L
            val now = System.currentTimeMillis()
            val nextScanTarget = if (settings.lastCheckMillis > 0) {
                var target = settings.lastCheckMillis + intervalMillis
                while (target <= now) {
                    target += intervalMillis
                }
                target
            } else {
                now + intervalMillis
            }

            builder.setContentTitle(context.getString(R.string.notif_status_active_title))
                .setContentText(context.getString(R.string.notif_status_last_scan, lastCheckStr))
                .setWhen(nextScanTarget)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(
                    android.R.drawable.ic_media_pause,
                    context.getString(R.string.action_pause),
                    pausePendingIntent
                )
        }

        return builder.build()
    }

    /**
     * Actualiza o publica la notificación permanente de estado en la barra de tareas.
     */
    fun updateStatusNotification(context: Context, settings: AppSettings) {
        if (!havePermission(context) || !settings.showPersistentNotification) {
            cancelStatusNotification(context)
            return
        }

        val notification = buildStatusNotification(context, settings)

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_STATUS, notification)
        } catch (e: SecurityException) {
            // Ignorar si se revocó el permiso de notificaciones dinámicamente
        }
    }

    /** Cancela la notificación permanente de estado. */
    fun cancelStatusNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIF_ID_STATUS)
        } catch (e: Exception) {
            // Ignorar errores al cancelar
        }
    }
}
