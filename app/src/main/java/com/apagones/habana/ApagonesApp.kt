package com.apagones.habana

import android.app.Application
import com.apagones.habana.notification.NotificationHelper
import com.apagones.habana.worker.MonitoringScheduler

/**
 * Application: crea el canal de notificaciones y programa el trabajo
 * periódico de monitoreo (cada 15 min) al arrancar la app.
 */
class ApagonesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Canal "Apagones" (alta prioridad), idempotente
        NotificationHelper.createChannel(this)
        // Programar la revisión periódica del canal
        MonitoringScheduler.schedule(this)
    }
}
