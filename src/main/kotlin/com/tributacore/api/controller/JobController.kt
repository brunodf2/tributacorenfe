package com.tributacore.api.controller

import com.tributacore.api.dto.JobCreateResponse
import com.tributacore.api.dto.JobDetailResponse
import com.tributacore.api.service.JobService
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/jobs")
class JobController(private val jobService: JobService) {

    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importZip(@RequestParam("file") file: MultipartFile): ResponseEntity<JobCreateResponse> {
        if (file.isEmpty) {
            return ResponseEntity.badRequest().build()
        }

        val contentType = file.contentType ?: ""
        val fileName = file.originalFilename ?: ""

        if (!contentType.contains("zip") && !fileName.lowercase().endsWith(".zip")) {
            return ResponseEntity.badRequest().build()
        }

        val response = jobService.createJob(file)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
    }

    @GetMapping("/{id}")
    fun getJob(@PathVariable id: UUID): ResponseEntity<JobDetailResponse> {
        return try {
            val response = jobService.getJob(id)
            ResponseEntity.ok(response)
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/{id}/result.csv")
    fun getResultCsv(@PathVariable id: UUID): ResponseEntity<Resource> {
        return try {
            val file = jobService.getResultFile(id)
            val resource = FileSystemResource(file)

            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"result-${id}.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(file.length())
                .body(resource)
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid request")))
    }
}
