package com.apagones.habana.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fuente de datos que consulta los posts desde la API REST de Supabase.
 * El backend en Supabase raspa el canal de Telegram cada 2 minutos y almacena los posts en la tabla 'posts'.
 */
class SupabasePostSource(
    /** Timeout de red por request, en milisegundos (15 segundos). */
    private val timeoutMillis: Int = 15_000
) {

    /**
     * Obtiene los posts almacenados en Supabase con id superior a [sinceId] (id > sinceId),
     * ordenados cronológicamente de más antiguo a más reciente.
     *
     * @param sinceId Id del último post procesado (0 para consultar desde el inicio).
     * @return Lista de [TelegramPost] parseados desde el JSON de Supabase.
     * @throws IOException Si ocurre un error de red, timeout o código de respuesta HTTP no exitoso.
     */
    suspend fun fetchPosts(sinceId: Long = 0L): List<TelegramPost> = withContext(Dispatchers.IO) {
        val endpointUrl = "${SupabaseConfig.BASE_URL}/rest/v1/posts" +
            "?select=id,text,timestamp,url" +
            "&id=gt.$sinceId" +
            "&order=id.asc" +
            "&limit=500"

        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            setRequestProperty("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Error HTTP $responseCode al consultar Supabase: ${connection.responseMessage}")
            }

            val responseBody = BufferedReader(
                InputStreamReader(connection.inputStream, Charsets.UTF_8)
            ).use { reader ->
                reader.readText()
            }

            val jsonArray = JSONArray(responseBody)
            val postsList = mutableListOf<TelegramPost>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getLong("id")
                val text = obj.optString("text", "")
                val timestamp = if (obj.has("timestamp") && !obj.isNull("timestamp")) {
                    obj.getLong("timestamp")
                } else {
                    System.currentTimeMillis()
                }
                val rawUrl = obj.optString("url", "")
                val url = if (rawUrl.isNotBlank()) rawUrl else "https://t.me/EmpresaElectricaDeLaHabana/$id"

                postsList.add(
                    TelegramPost(
                        idPost = id,
                        text = text,
                        urlPost = url,
                        timestamp = timestamp
                    )
                )
            }

            postsList.sortedBy { it.idPost }
        } finally {
            connection.disconnect()
        }
    }
}
