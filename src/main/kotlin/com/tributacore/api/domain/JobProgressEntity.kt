package com.tributacore.api.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "job_progress")
data class JobProgressEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val jobId: UUID,

    @Column(nullable = false)
    val xmlFileName: String,

    @Column(nullable = false)
    val processedAt: Instant = Instant.now(),

    @Column(nullable = false)
    val success: Boolean = true,

    var errorMessage: String? = null
)
