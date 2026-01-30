package com.tributacore.api.repository

import com.tributacore.api.domain.JobProgressEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface JobProgressRepository : JpaRepository<JobProgressEntity, UUID> {
    fun findByJobId(jobId: UUID): List<JobProgressEntity>
    fun countByJobId(jobId: UUID): Long
    fun countByJobIdAndSuccess(jobId: UUID, success: Boolean): Long
}
