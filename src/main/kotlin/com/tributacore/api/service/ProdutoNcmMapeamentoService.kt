package com.tributacore.api.service

import com.tributacore.api.domain.ProdutoNcmMapeamentoEntity
import com.tributacore.api.dto.ProdutoNcmMapeamentoRequest
import com.tributacore.api.dto.ProdutoNcmMapeamentoResponse
import com.tributacore.api.repository.NcmRepository
import com.tributacore.api.repository.ProdutoNcmMapeamentoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ProdutoNcmMapeamentoService(
    private val mapeamentoRepository: ProdutoNcmMapeamentoRepository,
    private val ncmRepository: NcmRepository,
    private val textNormalizer: TextNormalizer
) {
    private val logger = LoggerFactory.getLogger(ProdutoNcmMapeamentoService::class.java)

    /**
     * Busca mapeamento customizado para uma descrição de produto.
     * Retorna o NCM cadastrado se encontrar correspondência exata ou similar.
     */
    fun buscarMapeamento(descricaoProduto: String): ProdutoNcmMapeamentoEntity? {
        val normalizada = textNormalizer.normalizeForMapping(descricaoProduto)

        // 1. Busca exata pela descrição normalizada
        val exato = mapeamentoRepository.findByDescricaoNormalizadaAndAtivoTrue(normalizada)
        if (exato != null) {
            logger.debug("Mapeamento exato encontrado para '$descricaoProduto': NCM ${exato.ncmCodigo}")
            return exato
        }

        // 2. Busca por palavras-chave principais (fallback)
        val palavrasChave = extrairPalavrasChave(descricaoProduto)
        for (palavra in palavrasChave) {
            if (palavra.length >= 4) {
                val encontrados = mapeamentoRepository.findByDescricaoNormalizadaContainingAndAtivoTrue(palavra)
                if (encontrados.isNotEmpty()) {
                    // Retorna o primeiro match (pode melhorar com scoring)
                    val melhor = encontrados.maxByOrNull {
                        calcularSimilaridade(normalizada, it.descricaoNormalizada)
                    }
                    if (melhor != null && calcularSimilaridade(normalizada, melhor.descricaoNormalizada) > 0.5) {
                        logger.debug("Mapeamento por palavra-chave encontrado para '$descricaoProduto': NCM ${melhor.ncmCodigo}")
                        return melhor
                    }
                }
            }
        }

        return null
    }

    /**
     * Cria um novo mapeamento produto-NCM
     */
    @Transactional
    fun criar(request: ProdutoNcmMapeamentoRequest): ProdutoNcmMapeamentoResponse {
        val ncmSanitizado = textNormalizer.sanitizeNcm(request.ncmCodigo)

        // Validar se o NCM existe
        val ncm = ncmRepository.findById(ncmSanitizado).orElseThrow {
            IllegalArgumentException("NCM '$ncmSanitizado' não encontrado na base de dados")
        }

        val normalizada = textNormalizer.normalizeForMapping(request.descricaoProduto)

        // Verificar se já existe mapeamento para esta descrição
        if (mapeamentoRepository.existsByDescricaoNormalizadaAndAtivoTrue(normalizada)) {
            throw IllegalArgumentException("Já existe um mapeamento ativo para a descrição '${request.descricaoProduto}'")
        }

        val entity = ProdutoNcmMapeamentoEntity(
            descricaoProduto = request.descricaoProduto.trim(),
            descricaoNormalizada = normalizada,
            ncm = ncm
        )

        val saved = mapeamentoRepository.save(entity)
        logger.info("Mapeamento criado: '${request.descricaoProduto}' -> NCM $ncmSanitizado (${ncm.descricao})")

        return toResponse(saved)
    }

    /**
     * Cria múltiplos mapeamentos de uma vez
     */
    @Transactional
    fun criarEmLote(requests: List<ProdutoNcmMapeamentoRequest>): Pair<Int, List<String>> {
        var criados = 0
        val erros = mutableListOf<String>()

        for (request in requests) {
            try {
                criar(request)
                criados++
            } catch (e: Exception) {
                erros.add("${request.descricaoProduto}: ${e.message}")
            }
        }

        return criados to erros
    }

    /**
     * Lista todos os mapeamentos ativos
     */
    fun listarTodos(): List<ProdutoNcmMapeamentoResponse> {
        return mapeamentoRepository.findByAtivoTrue().map { toResponse(it) }
    }

    /**
     * Busca mapeamentos por termo
     */
    fun buscarPorTermo(termo: String): List<ProdutoNcmMapeamentoResponse> {
        return mapeamentoRepository.searchByDescricaoProduto(termo).map { toResponse(it) }
    }

    /**
     * Lista mapeamentos por NCM
     */
    fun listarPorNcm(ncmCodigo: String): List<ProdutoNcmMapeamentoResponse> {
        val sanitizado = textNormalizer.sanitizeNcm(ncmCodigo)
        return mapeamentoRepository.findByNcmCodigoAndAtivoTrue(sanitizado).map { toResponse(it) }
    }

    /**
     * Atualiza um mapeamento existente
     */
    @Transactional
    fun atualizar(id: Long, request: ProdutoNcmMapeamentoRequest): ProdutoNcmMapeamentoResponse {
        val existente = mapeamentoRepository.findById(id).orElseThrow {
            NoSuchElementException("Mapeamento não encontrado: $id")
        }

        val ncmSanitizado = textNormalizer.sanitizeNcm(request.ncmCodigo)
        val ncm = ncmRepository.findById(ncmSanitizado).orElseThrow {
            IllegalArgumentException("NCM '$ncmSanitizado' não encontrado na base de dados")
        }

        val normalizada = textNormalizer.normalizeForMapping(request.descricaoProduto)

        val atualizado = existente.copy(
            descricaoProduto = request.descricaoProduto.trim(),
            descricaoNormalizada = normalizada,
            ncm = ncm,
            atualizadoEm = Instant.now()
        )

        val saved = mapeamentoRepository.save(atualizado)
        logger.info("Mapeamento atualizado: ID $id -> '${request.descricaoProduto}' -> NCM $ncmSanitizado")

        return toResponse(saved)
    }

    /**
     * Desativa um mapeamento (soft delete)
     */
    @Transactional
    fun desativar(id: Long) {
        val existente = mapeamentoRepository.findById(id).orElseThrow {
            NoSuchElementException("Mapeamento não encontrado: $id")
        }

        val desativado = existente.copy(ativo = false, atualizadoEm = Instant.now())
        mapeamentoRepository.save(desativado)
        logger.info("Mapeamento desativado: ID $id")
    }

    /**
     * Reativa um mapeamento
     */
    @Transactional
    fun reativar(id: Long): ProdutoNcmMapeamentoResponse {
        val existente = mapeamentoRepository.findById(id).orElseThrow {
            NoSuchElementException("Mapeamento não encontrado: $id")
        }

        val reativado = existente.copy(ativo = true, atualizadoEm = Instant.now())
        val saved = mapeamentoRepository.save(reativado)
        logger.info("Mapeamento reativado: ID $id")

        return toResponse(saved)
    }

    private fun extrairPalavrasChave(texto: String): List<String> {
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

    private fun calcularSimilaridade(s1: String, s2: String): Double {
        val set1 = s1.split(" ").filter { it.isNotBlank() }.toSet()
        val set2 = s2.split(" ").filter { it.isNotBlank() }.toSet()

        if (set1.isEmpty() && set2.isEmpty()) return 1.0
        if (set1.isEmpty() || set2.isEmpty()) return 0.0

        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size

        return intersection.toDouble() / union.toDouble()
    }

    private fun toResponse(entity: ProdutoNcmMapeamentoEntity): ProdutoNcmMapeamentoResponse {
        return ProdutoNcmMapeamentoResponse(
            id = entity.id!!,
            descricaoProduto = entity.descricaoProduto,
            descricaoNormalizada = entity.descricaoNormalizada,
            ncmCodigo = entity.ncmCodigo,
            ncmDescricao = entity.ncmDescricao,
            ativo = entity.ativo,
            criadoEm = entity.criadoEm,
            atualizadoEm = entity.atualizadoEm
        )
    }
}
