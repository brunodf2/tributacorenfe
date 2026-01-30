package com.tributacore.api.repository

import com.tributacore.api.domain.NcmEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface NcmRepository : JpaRepository<NcmEntity, String> {
    fun existsByCodigo(codigo: String): Boolean

    @Query("SELECT n FROM NcmEntity n WHERE n.codigo LIKE :prefix%")
    fun findByCodigoStartingWith(prefix: String): List<NcmEntity>
}
