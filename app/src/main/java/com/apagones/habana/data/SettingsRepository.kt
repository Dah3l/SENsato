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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore único de la aplicación (preferencias simples).
 * Aquí se guardan: lista de circuitos, último id de post visto,
 * preferencia del parte nacional y marca de "primera vez".
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apagones_prefs")

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
    val paused: Boolean = false
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
    }

    /** Flujo reactivo con toda la configuración (la UI lo observa). */
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            circuits = prefs[Keys.CIRCUITS]?.sorted() ?: emptyList(),
            lastSeenPostId = prefs[Keys.LAST_SEEN_ID] ?: 0L,
            notifyNationalReport = prefs[Keys.NOTIFY_NATIONAL] ?: false,
            lastCheckMillis = prefs[Keys.LAST_CHECK] ?: 0L,
            paused = prefs[Keys.PAUSED] ?: false
        )
    }

    /** Lee la configuración una sola vez (para el worker, fuera de Compose). */
    suspend fun readOnce(): AppSettings {
        var result = AppSettings()
        context.dataStore.data.take(1).collect { prefs ->
            result = AppSettings(
                circuits = prefs[Keys.CIRCUITS]?.sorted() ?: emptyList(),
                lastSeenPostId = prefs[Keys.LAST_SEEN_ID] ?: 0L,
                notifyNationalReport = prefs[Keys.NOTIFY_NATIONAL] ?: false,
                lastCheckMillis = prefs[Keys.LAST_CHECK] ?: 0L,
                paused = prefs[Keys.PAUSED] ?: false
            )
        }
        return result
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

    /** Elimina un circuito de la lista. */
    suspend fun removeCircuit(circuit: String) {
        val normalized = normalizeCircuit(circuit)
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CIRCUITS] ?: return@edit
            prefs[Keys.CIRCUITS] = HashSet(current - normalized)
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
