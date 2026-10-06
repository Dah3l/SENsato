package com.apagones.habana.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apagones.habana.data.AppSettings
import com.apagones.habana.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla principal. Expone el estado de [SettingsRepository]
 * como StateFlow y ofrece acciones para mutarlo desde la UI.
 */
class MainViewModel(private val repo: SettingsRepository) : ViewModel() {

    /** Estado reactivo (circuitos, última revisión, pausa...). */
    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings()
        )

    /** Agrega un circuito normalizado; notifica el resultado por callbacks. */
    fun addCircuit(
        circuit: String,
        onAdded: () -> Unit,
        onDuplicate: () -> Unit
    ) {
        viewModelScope.launch {
            if (repo.addCircuit(circuit)) onAdded() else onDuplicate()
        }
    }

    /** Elimina un circuito de la lista. */
    fun removeCircuit(circuit: String) {
        viewModelScope.launch { repo.removeCircuit(circuit) }
    }

    /** Activa/desactiva las notificaciones del parte nacional. */
    fun setNotifyNational(enabled: Boolean) {
        viewModelScope.launch { repo.setNotifyNational(enabled) }
    }

    /** Pausa/reanuda el monitoreo en segundo plano. */
    fun setPaused(paused: Boolean) {
        viewModelScope.launch { repo.setPaused(paused) }
    }
}

/** Factory sencilla: el VM solo necesita el repositorio (sin DI framework). */
class MainViewModelFactory(private val repo: SettingsRepository) :
    androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(repo) as T
    }
}
