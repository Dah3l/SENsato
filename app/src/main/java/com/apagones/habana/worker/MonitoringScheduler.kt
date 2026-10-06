package com.apagones.habana.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Registro central del trabajo periódico.
 * Android solo permite intervalos mínimos de 15 minutos para PeriodicWorkRequest;
 * ese es exactamente el intervalo que usamos.
 */
object MonitoringScheduler {

    private const val WORK_NAME = "apagones_monitoring"

    /** Programa la revisión cada 15 minutos (reemplaza cualquier programación previa). */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MonitoringWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED) // requiere internet
                    .build()
            )
            .setInitialDelay(10, TimeUnit.SECONDS) // primera revisión casi inmediata
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // siempre reprograma con la config actual
            request
        )
    }

    /** Cancela el monitoreo en segundo plano (pausar desde la UI). */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
