package com.apagones.habana.data

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.apagones.habana.parser.MentionMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore único de la aplicación (preferencias simples).
 * Aquí se guardan: lista de circuitos, último id de post visto,
 * preferencia del parte nacional y marca de "primera vez".
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apagones_prefs")

/** Información del estado actual y timestamp de cambio de un circuito. */
data class CircuitStatusInfo(
    val isAffected: Boolean,
    val timestamp: Long
)

/** Estado observable que consume la interfaz. */
data class AppSettings(
    /** Circuitos que el usuario monitorea, en mayúsculas (ej: ["AL53", "GC19"]). */
    val circuits: List<String> = emptyList(),
    /** Id del último post procesado (0 = nunca se ha ejecutado el worker). */
    val lastSeenPostId: Long = 0L,
    /** true si el usuario quiere recibir también el parte nacional del SEN. */
    val notifyNationalReport: Boolean = false,
    /** Momento (epoch millis) de la última ejecución exitosa del worker. */
    val lastCheckMillis: Long = 0L,
    /** Monitoreo pausado por el usuario. */
    val paused: Boolean = false,
    /** Notificación permanente en la barra de tareas. */
    val showPersistentNotification: Boolean = true,
    /** Historial de avisos recibidos en la app. */
    val notifications: List<AppNotification> = emptyList(),
    /** Circuitos actualmente afectados según los reportes del canal. */
    val affectedCircuits: Set<String> = emptySet(),
    /** Circuitos que han recibido al menos un reporte desde que fueron agregados. */
    val knownCircuits: Set<String> = emptySet(),
    /** true si ya se completó el onboarding/tutorial inicial. */
    val onboardingCompleted: Boolean = false,
    /** Indica si los ajustes ya fueron cargados desde DataStore. */
    val isLoaded: Boolean = false,
    /** Mapa de estado y timestamp de cada circuito. */
    val circuitStatuses: Map<String, CircuitStatusInfo> = emptyMap()
)

/**
 * Repositorio de acceso a DataStore. Centraliza claves y normalización
 * (los circuitos siempre se guardan en mayúsculas y sin espacios).
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val CIRCUITS = stringSetPreferencesKey("circuits")
        val LAST_SEEN_ID = longPreferencesKey("last_seen_post_id")
        val NOTIFY_NATIONAL = booleanPreferencesKey("notify_national")
        val LAST_CHECK = longPreferencesKey("last_check_millis")
        val PAUSED = booleanPreferencesKey("paused")
        val SHOW_PERSISTENT_NOTIF = booleanPreferencesKey("show_persistent_notif")
        val NOTIFICATION_HISTORY = stringSetPreferencesKey("notification_history")
        val AFFECTED_CIRCUITS = stringSetPreferencesKey("affected_circuits")
        val KNOWN_CIRCUITS = stringSetPreferencesKey("known_circuits")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val CIRCUIT_STATUS_MAP = stringSetPreferencesKey("circuit_status_map")
    }

    /** Flujo reactivo con toda la configuración (la UI lo observa). */
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val notifSet = prefs[Keys.NOTIFICATION_HISTORY] ?: emptySet()
        val notifList = notifSet.mapNotNull { AppNotification.fromJson(it) }
            .sortedByDescending { it.timestamp }
        val affectedSet = prefs[Keys.AFFECTED_CIRCUITS] ?: emptySet()
        val knownSet = prefs[Keys.KNOWN_CIRCUITS] ?: emptySet()

        val statusMap = mutableMapOf<String, CircuitStatusInfo>()
        for (item in prefs[Keys.CIRCUIT_STATUS_MAP] ?: emptySet()) {
            val parts = item.split("|")
            if (parts.size == 3) {
                val circuit = parts[0]
                val time = parts[1].toLongOrNull() ?: 0L
                val affected = parts[2].toBoolean()
                statusMap[circuit] = CircuitStatusInfo(affected, time)
            }
        }

        AppSettings(
            circuits = prefs[Keys.CIRCUITS]?.sorted() ?: emptyList(),
            lastSeenPostId = prefs[Keys.LAST_SEEN_ID] ?: 0L,
            notifyNationalReport = prefs[Keys.NOTIFY_NATIONAL] ?: false,
            lastCheckMillis = prefs[Keys.LAST_CHECK] ?: 0L,
            paused = prefs[Keys.PAUSED] ?: false,
            showPersistentNotification = prefs[Keys.SHOW_PERSISTENT_NOTIF] ?: true,
            notifications = notifList,
            affectedCircuits = affectedSet,
            knownCircuits = knownSet,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            isLoaded = true,
            circuitStatuses = statusMap
        )
    }

    /** Lee la configuración una sola vez (para el worker, fuera de Compose). */
    suspend fun readOnce(): AppSettings {
        val prefs = context.dataStore.data.first()
        val notifSet = prefs[Keys.NOTIFICATION_HISTORY] ?: emptySet()
        val notifList = notifSet.mapNotNull { AppNotification.fromJson(it) }
            .sortedByDescending { it.timestamp }
        val affectedSet = prefs[Keys.AFFECTED_CIRCUITS] ?: emptySet()
        val knownSet = prefs[Keys.KNOWN_CIRCUITS] ?: emptySet()

        val statusMap = mutableMapOf<String, CircuitStatusInfo>()
        for (item in prefs[Keys.CIRCUIT_STATUS_MAP] ?: emptySet()) {
            val parts = item.split("|")
            if (parts.size == 3) {
                val circuit = parts[0]
                val time = parts[1].toLongOrNull() ?: 0L
                val affected = parts[2].toBoolean()
                statusMap[circuit] = CircuitStatusInfo(affected, time)
            }
        }

        return AppSettings(
            circuits = prefs[Keys.CIRCUITS]?.sorted() ?: emptyList(),
            lastSeenPostId = prefs[Keys.LAST_SEEN_ID] ?: 0L,
            notifyNationalReport = prefs[Keys.NOTIFY_NATIONAL] ?: false,
            lastCheckMillis = prefs[Keys.LAST_CHECK] ?: 0L,
            paused = prefs[Keys.PAUSED] ?: false,
            showPersistentNotification = prefs[Keys.SHOW_PERSISTENT_NOTIF] ?: true,
            notifications = notifList,
            affectedCircuits = affectedSet,
            knownCircuits = knownSet,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            isLoaded = true,
            circuitStatuses = statusMap
        )
    }

    /** Agrega un circuito (normalizado a mayúsculas). Devuelve false si ya existía. */
    suspend fun addCircuit(raw: String): Boolean {
        val normalized = normalizeCircuit(raw)
        if (normalized.isEmpty()) return false
        var added = false
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CIRCUITS] ?: mutableSetOf()
            if (!current.contains(normalized)) {
                prefs[Keys.CIRCUITS] = HashSet(current + normalized)
                added = true
            }
        }
        return added
    }

    /** Elimina un circuito de la lista y única y exclusivamente las notificaciones asignadas a este (por su título). */
    suspend fun removeCircuit(circuit: String) {
        val normalized = normalizeCircuit(circuit)
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CIRCUITS] ?: return@edit
            prefs[Keys.CIRCUITS] = HashSet(current - normalized)

            val affected = prefs[Keys.AFFECTED_CIRCUITS] ?: emptySet()
            if (affected.contains(normalized)) {
                prefs[Keys.AFFECTED_CIRCUITS] = HashSet(affected - normalized)
            }

            val known = prefs[Keys.KNOWN_CIRCUITS] ?: emptySet()
            if (known.contains(normalized)) {
                prefs[Keys.KNOWN_CIRCUITS] = HashSet(known - normalized)
            }

            val statusMap = prefs[Keys.CIRCUIT_STATUS_MAP] ?: emptySet()
            val updatedMap = statusMap.filter { !it.startsWith("$normalized|") }.toSet()
            prefs[Keys.CIRCUIT_STATUS_MAP] = updatedMap

            // Eliminar ÚNICA Y EXCLUSIVAMENTE las notificaciones asignadas a este circuito (comprobando su título).
            // No eliminamos las notificaciones asignadas a otros circuitos aunque el texto del cuerpo mencione ambos.
            val notifSet = prefs[Keys.NOTIFICATION_HISTORY] ?: emptySet()
            val filteredNotifs = notifSet.mapNotNull { AppNotification.fromJson(it) }
                .filter { notif ->
                    val isAssignedToThisCircuit = MentionMatcher.findMatchingCircuits(notif.title, listOf(normalized)).isNotEmpty() ||
                        notif.title.contains(normalized, ignoreCase = true)
                    !isAssignedToThisCircuit
                }
            prefs[Keys.NOTIFICATION_HISTORY] = filteredNotifs.map { it.toJson() }.toSet()
        }
    }

    /** Guarda el id del último post procesado (solo avanza, nunca retrocede). */
    suspend fun setLastSeenPostId(id: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.LAST_SEEN_ID] ?: 0L
            if (id > current) prefs[Keys.LAST_SEEN_ID] = id
        }
    }

    /** Marca la hora de la última revisión correcta. */
    suspend fun setLastCheck(millis: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_CHECK] = millis }
    }

    /** Activa/desactiva la notificación del parte nacional del SEN. */
    suspend fun setNotifyNational(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.NOTIFY_NATIONAL] = enabled }
    }

    /** Pausa/reanuda el monitoreo en segundo plano. */
    suspend fun setPaused(paused: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.PAUSED] = paused }
    }

    /** Muestra/oculta la notificación permanente de estado. */
    suspend fun setShowPersistentNotification(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SHOW_PERSISTENT_NOTIF] = enabled }
    }

    /** Marca si ya se completó el onboarding/tutorial de bienvenida. */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETED] = completed }
    }

    /** Agrega un aviso al historial (máximo 200). */
    suspend fun addNotification(notification: AppNotification) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.NOTIFICATION_HISTORY] ?: emptySet()
            val updated = mutableSetOf(notification.toJson())
            val parsed = current.mapNotNull { AppNotification.fromJson(it) }
                .sortedByDescending { it.timestamp }
                .take(199)
            for (item in parsed) {
                updated.add(item.toJson())
            }
            prefs[Keys.NOTIFICATION_HISTORY] = updated
        }
    }

    /** Limpia el historial de avisos. */
    suspend fun clearNotifications() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.NOTIFICATION_HISTORY)
        }
    }

    /**
     * Marca o desmarca circuitos como afectados según los reportes.
     * Si un circuito es mencionado nuevamente (incluso si reitera el mismo estado),
     * actualiza su información y su timestamp con el de la notificación más reciente.
     */
    suspend fun setCircuitsAffected(circuits: List<String>, affected: Boolean, timestamp: Long = System.currentTimeMillis()) {
        if (circuits.isEmpty()) return
        val now = timestamp
        context.dataStore.edit { prefs ->
            val currentAffected = prefs[Keys.AFFECTED_CIRCUITS] ?: emptySet()
            val updatedAffected = HashSet(currentAffected)
            val normalized = circuits.map { normalizeCircuit(it) }

            if (affected) {
                updatedAffected.addAll(normalized)
            } else {
                updatedAffected.removeAll(normalized)
            }
            prefs[Keys.AFFECTED_CIRCUITS] = updatedAffected

            // Actualizar mapa de estado con timestamp de la notificación más reciente
            val currentMapSet = prefs[Keys.CIRCUIT_STATUS_MAP] ?: emptySet()
            val map = currentMapSet.associate { item ->
                val parts = item.split("|")
                val circuit = if (parts.isNotEmpty()) parts[0] else ""
                val time = if (parts.size >= 2) parts[1].toLongOrNull() ?: now else now
                val isEff = if (parts.size >= 3) parts[2].toBoolean() else false
                circuit to Pair(isEff, time)
            }.toMutableMap()

            for (c in normalized) {
                // Actualiza siempre el estado y el timestamp con la notificación más reciente (sea cambio o repetición)
                map[c] = Pair(affected, now)
            }

            val newMapSet = map.map { "${it.key}|${it.value.second}|${it.value.first}" }.toSet()
            prefs[Keys.CIRCUIT_STATUS_MAP] = newMapSet
        }
    }

    /** Marca circuitos como conocidos (han aparecido en al menos un reporte). */
    suspend fun markCircuitsKnown(circuits: List<String>) {
        if (circuits.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.KNOWN_CIRCUITS] ?: emptySet()
            val updated = HashSet(current)
            val normalized = circuits.map { normalizeCircuit(it) }
            updated.addAll(normalized)
            prefs[Keys.KNOWN_CIRCUITS] = updated
        }
    }

    companion object {
        /** Normaliza un circuito: sin espacios internos extra y en MAYÚSCULAS. */
        fun normalizeCircuit(raw: String): String =
            raw.trim().uppercase().replace(Regex("\\s+"), "")

        /**
         * true si la app ya está excluida de la optimización de batería
         * (Doze). Si es false, conviene sugerir al usuario excluirla para
         * que las notificaciones no lleguen tarde.
         */
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }
    }
}
