package com.apagones.habana

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apagones.habana.data.SettingsRepository
import com.apagones.habana.ui.screen.MainScreen
import com.apagones.habana.ui.screen.MainViewModel
import com.apagones.habana.ui.screen.MainViewModelFactory
import com.apagones.habana.ui.theme.ApagonesHabanaTheme
import com.apagones.habana.worker.MonitoringScheduler

/**
 * Actividad única de la aplicación Mi Circuito. Compone [MainScreen] con Material 3.
 * Al tocar una notificación abre la app directamente en la pestaña de Historial de avisos.
 */
class MainActivity : ComponentActivity() {

    private var initialTabState by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Repositorio compartido (DataStore singleton por nombre de archivo)
        val repository = SettingsRepository(applicationContext)

        // Pestaña inicial solicitada (ej. por notificación)
        initialTabState = intent?.getIntExtra("OPEN_TAB", 0) ?: 0

        setContent {
            ApagonesHabanaTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModelFactory(repository)
                )

                // Al volver a la app, aseguramos que el monitoreo esté programado
                MonitoringScheduler.schedule(applicationContext)

                MainScreen(
                    viewModel = viewModel,
                    initialTab = initialTabState
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent.getIntExtra("OPEN_TAB", -1)
        if (tab >= 0) {
            initialTabState = tab
        }
    }
}
