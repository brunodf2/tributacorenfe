package com.tributacore.api.repository

import com.tributacore.api.domain.JobEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface JobRepository : JpaRepository<JobEntity, UUID>
