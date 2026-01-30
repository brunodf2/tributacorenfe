package com.tributacore.api.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class JobStatus {
    PENDING, PROCESSING, COMPLETED, FAILED
}

@Entity
@Table(name = "jobs")
data class JobEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val fileName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: JobStatus = JobStatus.PENDING,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    var startedAt: Instant? = null,

    var completedAt: Instant? = null,

    var resultFilePath: String? = null,

    var errorMessage: String? = null,

    var totalXmlFiles: Int = 0,

    var processedXmlFiles: Int = 0
)
