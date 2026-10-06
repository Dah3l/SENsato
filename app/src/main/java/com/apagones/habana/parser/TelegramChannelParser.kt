package com.apagones.habana.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parser AISLADO del canal público de Telegram vista web:
 * https://t.me/s/EmpresaElectricaDeLaHabana
 *
 * Toda la lógica de scraping vive aquí a propósito: si Telegram cambia el
 * HTML de t.me (clases o estructura), SOLO hay que actualizar esta clase,
 * el resto de la app no se entera.
 *
 * Estructura esperada (verificada sobre el HTML actual de t.me):
 *   div.tgme_widget_message_wrap           -> contenedor de cada post
 *       div.tgme_widget_message            -> lleva data-post="EmpresaElectricaDeLaHabana/<id>"
 *       div.tgme_widget_message_text       -> texto del mensaje
 * Nota: el atributo data-post vive en el nodo INTERNO .tgme_widget_message;
 * por robustez comprobamos también el propio wrap por si cambia.
 */
class TelegramChannelParser(
    /** Nombre público del canal (parte después de t.me/s/). */
    private val channelUsername: String = "EmpresaElectricaDeLaHabana",
    /** Timeout de red por request, en milisegundos. */
    private val timeoutMillis: Int = 15_000
) {

    /** URL de la vista web pública del canal (no requiere login). */
    val channelUrl: String get() = "https://t.me/s/$channelUsername"

    /**
     * Descarga la página del canal y devuelve los posts encontrados,
     * ordenados de más antiguo a más reciente.
     *
     * @throws java.io.IOException si falla la descarga (sin conexión, etc.)
     */
    suspend fun fetchPosts(): List<TelegramPost> = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(channelUrl)
            .userAgent(USER_AGENT) // User-Agent de navegador: t.me bloquea clientes genéricos
            .timeout(timeoutMillis)
            .get()

        doc.select("div.tgme_widget_message_wrap")
            .mapNotNull { wrap -> parsePost(wrap) }
            // Orden ascendente por id por si el HTML viniera desordenado
            .sortedBy { it.idPost }
    }

    /**
     * Extrae un [TelegramPost] de un nodo wrap. Devuelve null si el nodo
     * no tiene id válido o no tiene texto (mensajes solo-foto, etc.).
     */
    private fun parsePost(wrap: Element): TelegramPost? {
        // El id puede estar en el propio wrap o (caso actual) en el nodo interno
        // .tgme_widget_message. data-post="EmpresaElectricaDeLaHabana/12345"
        val dataPost = listOf(
            wrap.attr("data-post"),
            wrap.selectFirst(".tgme_widget_message")?.attr("data-post").orEmpty()
        ).firstOrNull { it.isNotBlank() }.orEmpty()

        val idPart = dataPost.substringAfterLast('/', "")
        val id = idPart.toLongOrNull() ?: return null

        // El texto puede estar en <div class="tgme_widget_message_text">.
        // Usamos .text() de Jsoup, que convierte <br/> en espacios; para
        // conservar saltos de línea reemplazamos <br> por \n antes.
        val textEl = wrap.selectFirst(".tgme_widget_message_text") ?: return null
        val text = htmlToPlainText(textEl.html())
        if (text.isBlank()) return null

        return TelegramPost(
            idPost = id,
            text = text,
            urlPost = "https://t.me/$channelUsername/$id"
        )
    }

    /**
     * Convierte HTML del mensaje en texto plano conservando los saltos
     * de línea (<br/> -> \n) y decodificando las entidades HTML (&amp; etc.).
     */
    private fun htmlToPlainText(html: String): String {
        // Jsoup.parseBodyFragment conserva el cuerpo sin cabeceras de documento
        val parsed = Jsoup.parseBodyFragment(
            html.replace(Regex("(?i)<br\\s*/?>"), "\n")
        )
        // body().wholeText() mantendría los \n insertados
        return parsed.body().wholeText()
            .replace('\u00A0', ' ') // nbsp -> espacio normal
            .trim()
    }

    companion object {
        /** Cadena de navegación estándar para que t.me sirva la vista web. */
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}
