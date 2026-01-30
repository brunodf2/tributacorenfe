package com.tributacore.api.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class AlertSeverity {
    INFO, WARNING, ERROR
}

@Entity
@Table(name = "job_alerts")
data class JobAlertEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val jobId: UUID,

    @Column(nullable = false)
    val xmlFileName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val severity: AlertSeverity,

    @Column(nullable = false, length = 1000)
    val message: String,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    val ncmOriginal: String? = null,

    val ncmSugerido: String? = null,

    val similaridade: Double? = null
)
