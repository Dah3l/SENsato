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
 * Receiver para las acciones de la notificación permanente ("Pausar" / "Reanudar").
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SettingsRepository(context)
                when (action) {
                    ACTION_PAUSE -> {
                        repo.setPaused(true)
                        MonitoringScheduler.cancel(context)
                    }
                    ACTION_RESUME -> {
                        repo.setPaused(false)
                        MonitoringScheduler.schedule(context)
                    }
                }
                val updatedSettings = repo.readOnce()
                NotificationHelper.updateStatusNotification(context, updatedSettings)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.apagones.habana.ACTION_PAUSE"
        const val ACTION_RESUME = "com.apagones.habana.ACTION_RESUME"
    }
}
