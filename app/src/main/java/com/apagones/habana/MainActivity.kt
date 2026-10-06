package com.apagones.habana

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apagones.habana.data.SettingsRepository
import com.apagones.habana.ui.screen.MainScreen
import com.apagones.habana.ui.screen.MainViewModel
import com.apagones.habana.ui.screen.MainViewModelFactory
import com.apagones.habana.ui.theme.ApagonesHabanaTheme
import com.apagones.habana.worker.MonitoringScheduler

/**
 * Actividad única de la aplicación. Compone [MainScreen] con Material 3.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Repositorio compartido (DataStore singleton por nombre de archivo)
        val repository = SettingsRepository(applicationContext)

        setContent {
            ApagonesHabanaTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModelFactory(repository)
                )

                // Al volver a la app, aseguramos que el monitoreo esté programado
                // (a menos que el usuario lo haya pausado; eso lo respeta el worker).
                MonitoringScheduler.schedule(applicationContext)

                MainScreen(viewModel = viewModel)
            }
        }
    }
}
