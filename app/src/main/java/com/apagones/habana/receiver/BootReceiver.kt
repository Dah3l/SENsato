package com.apagones.habana.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.apagones.habana.data.SettingsRepository
import com.apagones.habana.notification.NotificationHelper
import com.apagones.habana.worker.MonitoringScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver para restaurar el monitoreo y la notificación permanente tras reiniciar el dispositivo.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = SettingsRepository(context)
                    val settings = repo.readOnce()
                    if (!settings.paused) {
                        MonitoringScheduler.schedule(context)
                    }
                    NotificationHelper.updateStatusNotification(context, settings)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
