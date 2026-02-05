package com.tributacore.api.repository

import com.tributacore.api.domain.ProdutoNcmMapeamentoEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ProdutoNcmMapeamentoRepository : JpaRepository<ProdutoNcmMapeamentoEntity, Long> {

    /**
     * Busca mapeamento exato pela descrição normalizada
     */
    fun findByDescricaoNormalizadaAndAtivoTrue(descricaoNormalizada: String): ProdutoNcmMapeamentoEntity?

    /**
     * Busca mapeamentos que contenham parte da descrição normalizada
     */
    @Query("SELECT p FROM ProdutoNcmMapeamentoEntity p WHERE p.ativo = true AND p.descricaoNormalizada LIKE %:termo%")
    fun findByDescricaoNormalizadaContainingAndAtivoTrue(termo: String): List<ProdutoNcmMapeamentoEntity>

    /**
     * Busca todos os mapeamentos ativos para um NCM específico
     */
    @Query("SELECT p FROM ProdutoNcmMapeamentoEntity p WHERE p.ativo = true AND p.ncm.codigo = :ncmCodigo")
    fun findByNcmCodigoAndAtivoTrue(ncmCodigo: String): List<ProdutoNcmMapeamentoEntity>

    /**
     * Verifica se existe mapeamento para uma descrição específica
     */
    fun existsByDescricaoNormalizadaAndAtivoTrue(descricaoNormalizada: String): Boolean

    /**
     * Busca todos os mapeamentos ativos
     */
    fun findByAtivoTrue(): List<ProdutoNcmMapeamentoEntity>

    /**
     * Busca mapeamentos por termo na descrição do produto (case insensitive)
     */
    @Query("SELECT p FROM ProdutoNcmMapeamentoEntity p WHERE p.ativo = true AND LOWER(p.descricaoProduto) LIKE LOWER(CONCAT('%', :termo, '%'))")
    fun searchByDescricaoProduto(termo: String): List<ProdutoNcmMapeamentoEntity>
}
