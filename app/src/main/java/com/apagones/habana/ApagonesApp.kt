package com.apagones.habana

import android.app.Application
import com.apagones.habana.data.SettingsRepository
import com.apagones.habana.notification.NotificationHelper
import com.apagones.habana.worker.MonitoringScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application: crea los canales de notificaciones y programa el trabajo
 * periódico de monitoreo (cada 15 min) al arrancar la app.
 */
class ApagonesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Crear canales de notificaciones (alta prioridad y estado permanente)
        NotificationHelper.createChannel(this)

        // Programar la revisión periódica del canal
        MonitoringScheduler.schedule(this)

        // Actualizar la notificación permanente con el estado actual
        CoroutineScope(Dispatchers.IO).launch {
            val settings = SettingsRepository(this@ApagonesApp).readOnce()
            NotificationHelper.updateStatusNotification(this@ApagonesApp, settings)
        }
    }
}
