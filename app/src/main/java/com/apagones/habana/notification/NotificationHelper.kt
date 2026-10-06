package com.apagones.habana.notification

import android.Manifest
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
import com.apagones.habana.R

/**
 * Helper de notificaciones locales.
 * Crea el canal "Apagones" (alta prioridad) y publica avisos que, al tocarlos,
 * abren el post original del canal en el navegador.
 */
object NotificationHelper {

    /** Id del canal de notificaciones (nombre visible: "Apagones"). */
    const val CHANNEL_ID = "apagones_channel"

    /** Prefijo para generar ids únicos por post/circuito. */
    private const val NOTIF_ID_BASE = 4000

    /**
     * Crea el canal de notificaciones. Seguro llamarlo varias veces:
     * si ya existe, Android lo ignora. Llamar desde Application.onCreate().
     */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name), // "Apagones"
            NotificationManager.IMPORTANCE_HIGH              // alta prioridad: interrumpe
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
        }
        manager.createNotificationChannel(channel)
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
     * Publica una notificación local con el texto del aviso.
     *
     * @param title   Título (ej: "⚡ Apagón mencionado: AL53")
     * @param text    Cuerpo: fragmento o texto completo del post
     * @param postUrl URL del post en t.me, se abre al tocar la notificación
     * @param notifId Identificador único (se deriva del id del post + circuito)
     */
    fun showNotification(
        context: Context,
        title: String,
        text: String,
        postUrl: String,
        notifId: Int
    ) {
        if (!havePermission(context)) return

        // Intent implícito: abre https://t.me/EmpresaElectricaDeLaHabana/<id>
        // en el navegador (o en Telegram si está instalado; no usamos setPackage
        // a propósito para permitir ambos).
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(postUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId, // requestCode único por notificación
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            // BigTextStyle permite leer el aviso completo desplegado
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIF_ID_BASE + notifId, notification)
    }
}
