package com.tributacore.api.service

import com.tributacore.api.domain.NcmEntity
import com.tributacore.api.dto.NcmSuggestion
import com.tributacore.api.repository.NcmRepository
import org.springframework.stereotype.Service

@Service
class NcmCompatibilityService(
    private val ncmRepository: NcmRepository,
    private val textNormalizer: TextNormalizer,
    private val similarity: Similarity
) {

    companion object {
        private const val MIN_SIMILARITY_THRESHOLD = 0.3
    }

    fun validateAndSuggest(ncmOriginal: String, productDescription: String): NcmSuggestion {
        val ncmSanitizado = textNormalizer.sanitizeNcm(ncmOriginal)

        val ncmExistente = ncmRepository.findByCodigo(ncmSanitizado)

        if (ncmExistente != null) {
            return NcmSuggestion(
                ncmOriginal = ncmOriginal,
                ncmSanitizado = ncmSanitizado,
                valido = true,
                sugestao = null,
                descricaoSugestao = ncmExistente.descricao,
                similaridade = null
            )
        }

        val suggestion = findBestMatch(productDescription, ncmSanitizado)

        return NcmSuggestion(
            ncmOriginal = ncmOriginal,
            ncmSanitizado = ncmSanitizado,
            valido = false,
            sugestao = suggestion?.first?.codigo,
            descricaoSugestao = suggestion?.first?.descricao,
            similaridade = suggestion?.second
        )
    }

    private fun findBestMatch(productDescription: String, ncmSanitizado: String): Pair<NcmEntity, Double>? {
        val prefix = ncmSanitizado.take(4)
        val candidates = if (prefix.isNotEmpty()) {
            ncmRepository.findByCodigoStartingWith(prefix).ifEmpty {
                ncmRepository.findByCodigoStartingWith(ncmSanitizado.take(2))
            }
        } else {
            emptyList()
        }

        if (candidates.isEmpty()) return null

        return candidates
            .map { ncm ->
                val descToCompare = ncm.descricaoNormalizada ?: ncm.descricao
                val sim = similarity.combinedSimilarity(productDescription, descToCompare)
                ncm to sim
            }
            .filter { it.second >= MIN_SIMILARITY_THRESHOLD }
            .maxByOrNull { it.second }
    }

    fun isValidNcm(ncm: String): Boolean {
        val sanitized = textNormalizer.sanitizeNcm(ncm)
        return ncmRepository.existsByCodigo(sanitized)
    }
}
