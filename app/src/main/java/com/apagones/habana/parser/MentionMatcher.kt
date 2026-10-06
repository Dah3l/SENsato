package com.apagones.habana.parser

/**
 * Lógica pura de detección de menciones (sin dependencias de Android),
 * lo que la hace fácil de testear.
 */
object MentionMatcher {

    /** Frase que identifica el parte nacional del SEN en los posts. */
    const val NATIONAL_REPORT_PHRASE = "Situación del SEN"

    /**
     * Variante de la frase sin tildes: algunos posts escriben "Situacion del SEN"
     * y queremos seguir detectándolos aunque cambie la acentuación.
     */
    private const val NATIONAL_REPORT_PHRASE_NO_ACCENTS = "Situacion del SEN"

    enum class StatusType {
        RESTORED, AFFECTED
    }

    /** true si el post corresponde al parte nacional del SEN. */
    fun isNationalReport(text: String): Boolean =
        text.contains(NATIONAL_REPORT_PHRASE, ignoreCase = true) ||
            text.contains(NATIONAL_REPORT_PHRASE_NO_ACCENTS, ignoreCase = true)

    /**
     * Analiza el texto del post y determina si es un aviso de afectación o de restablecimiento
     * basándose en las palabras clave y dando prioridad a la primera palabra clave que aparezca
     * en el mensaje, así como a las pistas de emojis (🟢☑️ para operativo, 🚨‼️🚧📉🛑 para afectado).
     */
    fun detectStatusUpdate(text: String): StatusType? {
        val lower = text.lowercase()

        val restoredKeywords = listOf(
            "queda restablecido", "queda restablecio",
            "restablecido", "restablecio",
            "se restablece",
            "se restableció", "se restablecio",
            "restableció", "restablecio"
        )
        val affectedKeywords = listOf(
            "afectado",
            "afecta",
            "se afecta",
            "se afectó", "se afecto"
        )

        val firstRestoredIndex = restoredKeywords.minOfOrNull { keyword ->
            val idx = lower.indexOf(keyword)
            if (idx >= 0) idx else Int.MAX_VALUE
        } ?: Int.MAX_VALUE

        val firstAffectedIndex = affectedKeywords.minOfOrNull { keyword ->
            val idx = lower.indexOf(keyword)
            if (idx >= 0) idx else Int.MAX_VALUE
        } ?: Int.MAX_VALUE

        if (firstRestoredIndex == Int.MAX_VALUE && firstAffectedIndex == Int.MAX_VALUE) {
            return null
        }

        val hasRestoredEmoji = text.contains("🟢") || text.contains("☑️")
        val hasAffectedEmoji = text.contains("🚨") || text.contains("‼️") || text.contains("🚧") || text.contains("📉") || text.contains("🛑")

        return when {
            firstRestoredIndex < firstAffectedIndex -> StatusType.RESTORED
            firstAffectedIndex < firstRestoredIndex -> StatusType.AFFECTED
            hasRestoredEmoji && !hasAffectedEmoji -> StatusType.RESTORED
            hasAffectedEmoji && !hasRestoredEmoji -> StatusType.AFFECTED
            else -> StatusType.RESTORED
        }
    }

    /**
     * Devuelve los circuitos mencionados en [text].
     *
     * Reglas: coincidencia EXACTA del token (ej: "AL53" no debe casar con
     * "CAL530"), insensible a mayúsculas. Los tokens del texto se separan
     * por cualquier carácter que no sea letra, dígito o "/".
     */
    fun findMatchingCircuits(text: String, circuits: List<String>): List<String> {
        if (circuits.isEmpty() || text.isBlank()) return emptyList()
        // Tokens normalizados del post (mayúsculas)
        val tokens = tokenize(text)
        return circuits.filter { circuit ->
            val target = circuit.uppercase()
            tokens.any { it == target }
        }
    }

    /**
     * Divide el texto en tokens alfanuméricos (conservando "/" dentro del
     * token, porque algunos avisos escriben p.ej. "11/AL53"). Todo en MAYÚSCULAS.
     */
    private fun tokenize(text: String): Set<String> =
        text.uppercase()
            .split(Regex("[^A-Z0-9/]+"))
            .filter { it.isNotEmpty() }
            .flatMap { token ->
                // "11/AL53" también debe poder casar con "AL53"
                val parts = token.split("/").filter { it.isNotEmpty() }
                buildList {
                    add(token)
                    addAll(parts)
                }
            }
            .toSet()
}
