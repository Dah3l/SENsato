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

    /** true si el post corresponde al parte nacional del SEN. */
    fun isNationalReport(text: String): Boolean =
        text.contains(NATIONAL_REPORT_PHRASE, ignoreCase = true) ||
            text.contains(NATIONAL_REPORT_PHRASE_NO_ACCENTS, ignoreCase = true)

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
