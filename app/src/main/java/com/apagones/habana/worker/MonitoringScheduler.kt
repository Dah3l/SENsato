package com.apagones.habana.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Registro central del trabajo basado en OneTimeWorkRequest
 * para permitir un intervalo de 2 minutos (WorkManager restringe PeriodicWork a mínimo 15 min).
 */
object MonitoringScheduler {

    private const val WORK_NAME = "apagones_monitoring"

    /** Programa la primera revisión (inmediata o con pequeño retraso). */
    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<MonitoringWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED) // requiere internet
                    .build()
            )
            .setInitialDelay(10, TimeUnit.SECONDS) // primera revisión casi inmediata
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Programa el siguiente escaneo en 2 minutos (llamado por el Worker al finalizar). */
    fun scheduleNext(context: Context) {
        val request = OneTimeWorkRequestBuilder<MonitoringWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(2, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Cancela el monitoreo en segundo plano (pausar desde la UI). */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
