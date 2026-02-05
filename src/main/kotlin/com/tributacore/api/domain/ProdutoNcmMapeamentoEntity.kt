package com.tributacore.api.domain

import jakarta.persistence.*
import java.time.Instant

/**
 * Tabela de mapeamento customizado entre descrição do produto (NFe) e NCM validado.
 * Permite cadastrar manualmente produtos cujas descrições não batem com a descrição oficial do NCM.
 * A descrição oficial do NCM é obtida diretamente da tabela NCM através do relacionamento.
 */
@Entity
@Table(
    name = "produto_ncm_mapeamento",
    indexes = [
        Index(name = "idx_produto_ncm_descricao_normalizada", columnList = "descricao_normalizada"),
        Index(name = "idx_produto_ncm_ncm_codigo", columnList = "ncm_codigo")
    ]
)
data class ProdutoNcmMapeamentoEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /**
     * Descrição do produto como aparece na NFe (ex: "COXAO MOLE KG")
     */
    @Column(name = "descricao_produto", nullable = false, length = 500)
    val descricaoProduto: String,

    /**
     * Descrição normalizada para busca (lowercase, sem acentos, sem caracteres especiais)
     */
    @Column(name = "descricao_normalizada", nullable = false, length = 500)
    val descricaoNormalizada: String,

    /**
     * Relacionamento com a tabela NCM para obter a descrição oficial
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ncm_codigo", referencedColumnName = "codigo", nullable = false)
    val ncm: NcmEntity,

    /**
     * Indica se este mapeamento está ativo
     */
    @Column(nullable = false)
    val ativo: Boolean = true,

    @Column(name = "criado_em", nullable = false)
    val criadoEm: Instant = Instant.now(),

    @Column(name = "atualizado_em", nullable = false)
    val atualizadoEm: Instant = Instant.now()
) {
    /**
     * Código NCM (helper para facilitar acesso)
     */
    val ncmCodigo: String
        get() = ncm.codigo

    /**
     * Descrição oficial do NCM (helper para facilitar acesso)
     */
    val ncmDescricao: String
        get() = ncm.descricao
}
