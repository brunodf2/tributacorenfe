package com.tributacore.api.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "ncm")
data class NcmEntity(
    @Id
    @Column(name = "codigo", nullable = false, length = 8)
    val codigo: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val descricao: String,

    @Column(name = "atualizado_em", nullable = false)
    val atualizadoEm: Instant = Instant.now()
)
