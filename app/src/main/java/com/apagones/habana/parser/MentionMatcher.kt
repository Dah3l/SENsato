package com.apagones.habana.parser

/**
 * Lógica pura de detección de menciones y estados.
 * Los emojis ✅, 🟢 o ☑️ por sí solos bastan para catalogar un mensaje como restablecido.
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
     * Analiza el texto del post por párrafos y devuelve un mapa con el estado detectado
     * para cada circuito mencionado (ej: "L325" -> StatusType.RESTORED, "GC19" -> StatusType.AFFECTED).
     */
    fun detectCircuitStatuses(text: String, circuits: List<String>): Map<String, StatusType> {
        if (circuits.isEmpty() || text.isBlank()) return emptyMap()

        // Si el texto contiene "Actualización de afectaciones" (con o sin tilde, insensible a mayúsculas),
        // trátalo como puramente informativo y no detectes ningún cambio de estado en él.
        if (text.contains("Actualización de afectaciones", ignoreCase = true) ||
            text.contains("Actualizacion de afectaciones", ignoreCase = true)) {
            return emptyMap()
        }

        val paragraphs = text.split(Regex("\n+"))
        val globalStatus = detectGlobalStatus(text)
        val result = mutableMapOf<String, StatusType>()

        for (circuit in circuits) {
            val target = circuit.uppercase()
            val matchingParagraphs = paragraphs.filter { p ->
                tokenize(p).any { it == target }
            }

            var circuitStatus: StatusType? = null
            for (p in matchingParagraphs) {
                val pStatus = detectGlobalStatus(p)
                if (pStatus != null) {
                    circuitStatus = pStatus
                    break
                }
            }
            result[target] = circuitStatus ?: globalStatus ?: StatusType.AFFECTED
        }
        return result
    }

    /**
     * Detecta el estado global (Restored o Affected) en un bloque de texto.
     * Regla principal: Los emojis ✅, 🟢 o ☑️ bastan por sí solos para catalogarlo como RESTORED.
     */
    fun detectGlobalStatus(text: String): StatusType? {
        // Si el texto contiene "Actualización de afectaciones" (con o sin tilde, insensible a mayúsculas),
        // trátalo como puramente informativo y no detectes ningún estado.
        if (text.contains("Actualización de afectaciones", ignoreCase = true) ||
            text.contains("Actualizacion de afectaciones", ignoreCase = true)) {
            return null
        }

        val lower = text.lowercase()

        // 1. Si contiene cualquiera de los emojis de restablecimiento (✅, 🟢, ☑️), basta por sí solo.
        val hasRestoredEmoji = text.contains("🟢") || text.contains("☑️") || text.contains("✅")
        if (hasRestoredEmoji) {
            return StatusType.RESTORED
        }

        val hasAffectedEmoji = text.contains("🚨") || text.contains("‼️") || text.contains("🚧") || text.contains("📉") || text.contains("🛑")
        if (hasAffectedEmoji) return StatusType.AFFECTED

        // 2. Palabras clave definitivas de restablecimiento
        val restoredKeywords = listOf(
            "queda restablecido", "queda restablecio",
            "servicio restablecido", "servicio reestablecido",
            "restablecido", "reestablecido", "restablecio",
            "se restablece",
            "se restableció", "se restablecio",
            "restableció", "restablecio",
            "recuperado", "servicio recuperado",
            "normalizado", "servicio normalizado",
            "energizado", "circuito energizado"
        )

        // 3. Palabras clave definitivas de afectación
        val affectedKeywords = listOf(
            "se afectó", "se afecto",
            "afectado", "afectados",
            "se afecta",
            "por déficit de generación", "por deficit de generacion",
            "déficit de generación", "deficit de generacion",
            "interrupción", "interrupcion",
            "fuera de servicio"
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

        return when {
            firstRestoredIndex < firstAffectedIndex -> StatusType.RESTORED
            firstAffectedIndex < firstRestoredIndex -> StatusType.AFFECTED
            else -> StatusType.RESTORED
        }
    }

    /**
     * Método de compatibilidad hacia atrás.
     */
    fun detectStatusUpdate(text: String): StatusType? = detectGlobalStatus(text)

    /**
     * Devuelve los circuitos mencionados en [text].
     */
    fun findMatchingCircuits(text: String, circuits: List<String>): List<String> {
        if (circuits.isEmpty() || text.isBlank()) return emptyList()
        val tokens = tokenize(text)
        return circuits.filter { circuit ->
            val target = circuit.uppercase()
            tokens.any { it == target }
        }
    }

    /**
     * Divide el texto en tokens alfanuméricos (conservando "/" dentro del token).
     */
    private fun tokenize(text: String): Set<String> =
        text.uppercase()
            .split(Regex("[^A-Z0-9/]+"))
            .filter { it.isNotEmpty() }
            .flatMap { token ->
                val parts = token.split("/").filter { it.isNotEmpty() }
                buildList {
                    add(token)
                    addAll(parts)
                }
            }
            .toSet()
}
