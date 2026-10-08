package com.apagones.habana.parser

/**
 * Representa un post del canal público de Telegram.
 *
 * @param idPost   Identificador numérico del post (la parte después de "EmpresaElectricaDeLaHabana/").
 *                 Se usa como "último visto" porque Telegram lo asigna de forma creciente.
 * @param text     Texto plano completo del mensaje (sin etiquetas HTML).
 * @param urlPost  URL web del post, para abrirla en el navegador al tocar la notificación.
 * @param timestamp Timestamp (en milisegundos epoch) del mensaje en Telegram.
 */
data class TelegramPost(
    val idPost: Long,
    val text: String,
    val urlPost: String,
    val timestamp: Long
)
