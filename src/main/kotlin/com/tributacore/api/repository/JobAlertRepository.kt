package com.tributacore.api.repository

import com.tributacore.api.domain.JobAlertEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface JobAlertRepository : JpaRepository<JobAlertEntity, UUID> {
    fun findByJobId(jobId: UUID): List<JobAlertEntity>
    fun countByJobId(jobId: UUID): Long
}
