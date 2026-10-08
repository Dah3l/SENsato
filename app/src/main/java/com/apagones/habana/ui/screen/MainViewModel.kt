package com.apagones.habana.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apagones.habana.data.AppSettings
import com.apagones.habana.data.AppNotification
import com.apagones.habana.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    // --- Búsqueda y filtrado de notificaciones en tiempo real ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Actualiza el texto de búsqueda ingresado por el usuario. */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Lista filtrada de notificaciones según el término de búsqueda en el título (ej. código de circuito), de forma insensible a mayúsculas y con coincidencia parcial. */
    val filteredNotifications: StateFlow<List<AppNotification>> = combine(
        settings,
        _searchQuery
    ) { appSettings, query ->
        if (query.isBlank()) {
            appSettings.notifications
        } else {
            val q = query.trim().lowercase()
            appSettings.notifications.filter { notif ->
                notif.title.lowercase().contains(q)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
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

    /** Muestra/oculta la notificación permanente de estado. */
    fun setShowPersistentNotification(enabled: Boolean) {
        viewModelScope.launch { repo.setShowPersistentNotification(enabled) }
    }

    /** Limpia el historial de avisos. */
    fun clearNotifications() {
        viewModelScope.launch { repo.clearNotifications() }
    }

    /** Marca si ya se completó el onboarding. */
    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch { repo.setOnboardingCompleted(completed) }
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
