package com.tributacore.api.service

import org.springframework.stereotype.Component
import java.text.Normalizer

@Component
class TextNormalizer {

    fun normalize(text: String): String {
        return text
            .lowercase()
            .let { removeAccents(it) }
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Normalização específica para mapeamento de produtos.
     * Remove palavras comuns de unidade de medida e códigos.
     */
    fun normalizeForMapping(text: String): String {
        val stopWords = setOf(
            "un", "und", "unid", "unidade", "unidades",
            "kg", "kgs", "gr", "grs", "g", "mg",
            "ml", "mls", "lt", "lts", "l",
            "cx", "caixa", "pct", "pacote", "pc", "pç", "peca",
            "ref", "cod", "codigo",
            "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas",
            "com", "sem", "para", "por", "e", "ou", "a", "o", "as", "os"
        )

        return text
            .lowercase()
            .let { removeAccents(it) }
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 && it !in stopWords }
            .joinToString(" ")
            .trim()
    }

    fun tokenize(text: String): Set<String> {
        return normalize(text)
            .split(" ")
            .filter { it.length > 1 }
            .toSet()
    }

    private fun removeAccents(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
    }

    fun sanitizeNcm(ncm: String): String {
        val digits = ncm.replace(Regex("[^0-9]"), "")
        return when {
            digits.length >= 8 -> digits.substring(0, 8)
            digits.length < 8 -> digits.padEnd(8, '0')
            else -> digits
        }
    }

    fun isValidNcmFormat(ncm: String): Boolean {
        val sanitized = sanitizeNcm(ncm)
        return sanitized.length == 8 && sanitized.all { it.isDigit() }
    }
}
