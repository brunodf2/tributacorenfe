package com.tributacore.api.service

import com.tributacore.api.domain.NcmEntity
import com.tributacore.api.dto.NcmSuggestion
import com.tributacore.api.repository.NcmRepository
import com.tributacore.api.repository.ProdutoNcmMapeamentoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NcmCompatibilityService(
    private val ncmRepository: NcmRepository,
    private val mapeamentoRepository: ProdutoNcmMapeamentoRepository,
    private val textNormalizer: TextNormalizer,
    private val similarity: Similarity
) {
    private val logger = LoggerFactory.getLogger(NcmCompatibilityService::class.java)

    companion object {
        private const val MIN_SIMILARITY_THRESHOLD = 0.011 // 1,1% - limiar mínimo para sugestões
        private const val DESCRIPTION_MATCH_THRESHOLD = 0.011 // 1,1% - Limiar para considerar descrição compatível
        private const val PRODUCT_NAME_MATCH_THRESHOLD = 0.011 // 1,1% - Limiar para busca por nome do produto
        private const val MAX_GLOBAL_SEARCH_RESULTS = 500 // Limite para busca global
        private const val MAPEAMENTO_SIMILARITY_THRESHOLD = 0.5 // Limiar para aceitar mapeamento customizado
    }

    fun validateAndSuggest(ncmOriginal: String, productDescription: String): NcmSuggestion {
        val ncmSanitizado = textNormalizer.sanitizeNcm(ncmOriginal)

        // 1. PRIMEIRO: Verificar se existe mapeamento customizado para este produto
        val mapeamento = buscarMapeamentoCustomizado(productDescription)
        if (mapeamento != null) {
            logger.debug("Mapeamento customizado encontrado para '$productDescription': NCM ${mapeamento.ncmCodigo}")

            val ncmCorreto = mapeamento.ncmCodigo
            val ncmMapeadoEntity = ncmRepository.findById(ncmCorreto).orElse(null)

            return NcmSuggestion(
                ncmOriginal = ncmOriginal,
                ncmSanitizado = ncmSanitizado,
                valido = ncmRepository.existsByCodigo(ncmSanitizado),
                sugestao = if (ncmSanitizado != ncmCorreto) ncmCorreto else null,
                descricaoSugestao = ncmMapeadoEntity?.descricao ?: mapeamento.ncmDescricao,
                similaridade = 1.0, // Mapeamento manual = 100% de confiança
                descricaoCompativel = ncmSanitizado == ncmCorreto,
                ncmSugeridoDescricao = if (ncmSanitizado != ncmCorreto) mapeamento.ncmDescricao else null,
                ncmSugeridoSimilaridade = if (ncmSanitizado != ncmCorreto) 1.0 else null,
                mapeamentoCustomizado = true
            )
        }

        // 2. Se não há mapeamento customizado, usar lógica de similaridade existente
        val ncmExistente = ncmRepository.findById(ncmSanitizado).orElse(null)

        if (ncmExistente != null) {
            // NCM existe - calcular similaridade entre descrição do produto e descrição oficial do NCM
            val normalizedProductDesc = textNormalizer.normalize(productDescription)
            val normalizedNcmDesc = textNormalizer.normalize(ncmExistente.descricao)
            val descriptionSimilarity = similarity.combinedSimilarity(normalizedProductDesc, normalizedNcmDesc)

            val isDescriptionCompatible = descriptionSimilarity >= DESCRIPTION_MATCH_THRESHOLD

            // Se descrição não é compatível, buscar NCM melhor por nome do produto
            val betterMatch = if (!isDescriptionCompatible) {
                findNcmByProductName(productDescription, ncmSanitizado)
            } else null

            return NcmSuggestion(
                ncmOriginal = ncmOriginal,
                ncmSanitizado = ncmSanitizado,
                valido = true,
                sugestao = betterMatch?.first?.codigo,
                descricaoSugestao = ncmExistente.descricao,
                similaridade = descriptionSimilarity,
                descricaoCompativel = isDescriptionCompatible,
                ncmSugeridoDescricao = betterMatch?.first?.descricao,
                ncmSugeridoSimilaridade = betterMatch?.second,
                mapeamentoCustomizado = false
            )
        }

        // NCM não existe - buscar sugestão por nome do produto
        val suggestion = findNcmByProductName(productDescription, ncmSanitizado)

        return NcmSuggestion(
            ncmOriginal = ncmOriginal,
            ncmSanitizado = ncmSanitizado,
            valido = false,
            sugestao = suggestion?.first?.codigo,
            descricaoSugestao = suggestion?.first?.descricao,
            similaridade = suggestion?.second,
            descricaoCompativel = false,
            ncmSugeridoDescricao = suggestion?.first?.descricao,
            ncmSugeridoSimilaridade = suggestion?.second,
            mapeamentoCustomizado = false
        )
    }

    /**
     * Busca mapeamento customizado para uma descrição de produto.
     */
    private fun buscarMapeamentoCustomizado(descricaoProduto: String): com.tributacore.api.domain.ProdutoNcmMapeamentoEntity? {
        val normalizada = textNormalizer.normalizeForMapping(descricaoProduto)

        // 1. Busca exata pela descrição normalizada
        val exato = mapeamentoRepository.findByDescricaoNormalizadaAndAtivoTrue(normalizada)
        if (exato != null) {
            return exato
        }

        // 2. Busca por palavras-chave principais
        val palavrasChave = extrairPalavrasChaveMapeamento(descricaoProduto)
        for (palavra in palavrasChave) {
            if (palavra.length >= 4) {
                val encontrados = mapeamentoRepository.findByDescricaoNormalizadaContainingAndAtivoTrue(palavra)
                if (encontrados.isNotEmpty()) {
                    val melhor = encontrados.maxByOrNull {
                        calcularSimilaridadeJaccard(normalizada, it.descricaoNormalizada)
                    }
                    if (melhor != null && calcularSimilaridadeJaccard(normalizada, melhor.descricaoNormalizada) >= MAPEAMENTO_SIMILARITY_THRESHOLD) {
                        return melhor
                    }
                }
            }
        }

        return null
    }

    private fun extrairPalavrasChaveMapeamento(texto: String): List<String> {
        val stopWords = setOf(
            "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas",
            "com", "sem", "para", "por", "un", "und", "unid", "unidade",
            "kg", "gr", "ml", "lt", "cx", "pct", "pc", "un", "ref", "cod"
        )

        return texto
            .lowercase()
            .replace(Regex("[^a-záéíóúãõâêîôûç\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in stopWords }
            .distinct()
    }

    private fun calcularSimilaridadeJaccard(s1: String, s2: String): Double {
        val set1 = s1.split(" ").filter { it.isNotBlank() }.toSet()
        val set2 = s2.split(" ").filter { it.isNotBlank() }.toSet()

        if (set1.isEmpty() && set2.isEmpty()) return 1.0
        if (set1.isEmpty() || set2.isEmpty()) return 0.0

        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size

        return intersection.toDouble() / union.toDouble()
    }

    /**
     * Busca o melhor NCM baseado no nome do produto (xProd).
     * Faz uma busca global na base de NCM comparando o nome do produto com as descrições.
     */
    fun findNcmByProductName(productName: String, excludeNcm: String? = null): Pair<NcmEntity, Double>? {
        val normalizedProductName = textNormalizer.normalize(productName)

        // Extrair palavras-chave do nome do produto para busca
        val keywords = extractKeywords(productName)

        // Buscar candidatos por palavras-chave na descrição
        val candidates = mutableSetOf<NcmEntity>()

        for (keyword in keywords) {
            if (keyword.length >= 3) { // Palavras com pelo menos 3 caracteres
                val found = ncmRepository.findByDescricaoContainingIgnoreCase(keyword)
                candidates.addAll(found)

                // Limitar para não sobrecarregar
                if (candidates.size >= MAX_GLOBAL_SEARCH_RESULTS) break
            }
        }

        // Se não encontrou nada por palavra-chave, fazer busca mais ampla por prefixo de NCM
        if (candidates.isEmpty() && excludeNcm != null && excludeNcm.length >= 2) {
            val prefix = excludeNcm.take(2)
            candidates.addAll(ncmRepository.findByCodigoStartingWith(prefix))
        }

        if (candidates.isEmpty()) return null

        // Filtrar o NCM excluído
        val filteredCandidates = if (excludeNcm != null) {
            candidates.filter { it.codigo != excludeNcm }
        } else {
            candidates.toList()
        }

        if (filteredCandidates.isEmpty()) return null

        // Calcular similaridade e retornar o melhor match
        return filteredCandidates
            .map { ncm ->
                val normalizedNcmDesc = textNormalizer.normalize(ncm.descricao)
                val sim = similarity.combinedSimilarity(normalizedProductName, normalizedNcmDesc)
                ncm to sim
            }
            .filter { it.second >= PRODUCT_NAME_MATCH_THRESHOLD }
            .maxByOrNull { it.second }
    }

    /**
     * Extrai palavras-chave relevantes do nome do produto
     */
    private fun extractKeywords(productName: String): List<String> {
        val stopWords = setOf(
            "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas",
            "com", "sem", "para", "por", "un", "und", "unid", "unidade",
            "kg", "gr", "ml", "lt", "l", "cx", "pct", "pc", "pç", "un",
            "ref", "cod", "c", "e", "ou", "a", "o", "as", "os"
        )

        return productName
            .lowercase()
            .replace(Regex("[^a-záéíóúãõâêîôûç\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in stopWords }
            .distinct()
            .take(5) // Limitar a 5 palavras-chave mais relevantes
    }


    fun isValidNcm(ncm: String): Boolean {
        val sanitized = textNormalizer.sanitizeNcm(ncm)
        return ncmRepository.existsByCodigo(sanitized)
    }
}
