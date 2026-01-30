package com.tributacore.api.domain

import jakarta.persistence.*

@Entity
@Table(name = "ncm", indexes = [Index(name = "idx_ncm_codigo", columnList = "codigo")])
data class NcmEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 8)
    val codigo: String,

    @Column(nullable = false, length = 500)
    val descricao: String,

    @Column(length = 500)
    val descricaoNormalizada: String? = null
)
