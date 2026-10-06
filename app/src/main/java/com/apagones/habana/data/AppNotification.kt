package com.apagones.habana.data

import org.json.JSONObject

/**
 * Modelo de datos para un aviso o notificación registrada en el historial de la app.
 */
data class AppNotification(
    val id: Long,
    val title: String,
    val text: String,
    val postUrl: String,
    val timestamp: Long
) {
    fun toJson(): String {
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("text", text)
            .put("url", postUrl)
            .put("timestamp", timestamp)
            .toString()
    }

    companion object {
        fun fromJson(jsonStr: String): AppNotification? {
            return try {
                val obj = JSONObject(jsonStr)
                AppNotification(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    text = obj.getString("text"),
                    postUrl = obj.getString("url"),
                    timestamp = obj.getLong("timestamp")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
