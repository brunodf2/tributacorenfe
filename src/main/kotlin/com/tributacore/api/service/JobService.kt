package com.tributacore.api.service

import com.tributacore.api.domain.*
import com.tributacore.api.dto.*
import com.tributacore.api.repository.JobAlertRepository
import com.tributacore.api.repository.JobProgressRepository
import com.tributacore.api.repository.JobRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

@Service
class JobService(
    private val jobRepository: JobRepository,
    private val jobProgressRepository: JobProgressRepository,
    private val jobAlertRepository: JobAlertRepository,
    private val nfeXmlExtractor: NfeXmlExtractor,
    private val nfeValidationCore: NfeValidationCore,
    private val textNormalizer: TextNormalizer
) {
    private val logger = LoggerFactory.getLogger(JobService::class.java)

    @Value("\${tributacore.storage.result-path:./results}")
    private lateinit var resultPath: String

    @Value("\${tributacore.limits.max-zip-size-mb:100}")
    private var maxZipSizeMb: Long = 100

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        private const val CSV_HEADER = "xml_file,chave_nfe,numero_nfe,n_item,c_prod,x_prod,ncm_original,ncm_sanitizado,ncm_valido,descricao_ncm_oficial,descricao_compativel,ncm_sugerido,similaridade,status,observacao"
    }

    @Transactional
    fun createJob(file: MultipartFile): JobCreateResponse {
        val maxBytes = maxZipSizeMb * 1024 * 1024
        if (file.size > maxBytes) {
            throw IllegalArgumentException("ZIP file exceeds maximum size of ${maxZipSizeMb}MB")
        }

        val job = JobEntity(
            fileName = file.originalFilename ?: "unknown.zip"
        )
        jobRepository.save(job)

        val tempFile = File.createTempFile("job-${job.id}", ".zip")
        file.transferTo(tempFile)

        // Schedule async processing after transaction commits
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                executor.submit {
                    processJobAsync(job.id, tempFile)
                }
            }
        })

        return JobCreateResponse(
            id = job.id,
            message = "Job created successfully. Processing started."
        )
    }

    private fun processJobAsync(jobId: UUID, zipFile: File) {
        try {
            val job = jobRepository.findById(jobId).orElseThrow {
                IllegalStateException("Job not found: $jobId")
            }

            job.status = JobStatus.PROCESSING
            job.startedAt = Instant.now()
            jobRepository.save(job)

            val resultDir = File(resultPath).apply { mkdirs() }
            val resultFile = File(resultDir, "result-${jobId}.csv")

            val xmlEntries = countXmlEntries(zipFile)
            job.totalXmlFiles = xmlEntries
            jobRepository.save(job)

            PrintWriter(FileWriter(resultFile)).use { writer ->
                writer.println(CSV_HEADER)

                ZipInputStream(zipFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.lowercase().endsWith(".xml")) {
                            processXmlEntry(jobId, entry.name, zis.readBytes(), writer)

                            job.processedXmlFiles++
                            jobRepository.save(job)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            job.status = JobStatus.COMPLETED
            job.completedAt = Instant.now()
            job.resultFilePath = resultFile.absolutePath
            jobRepository.save(job)

            logger.info("Job $jobId completed successfully")

        } catch (e: Exception) {
            logger.error("Job $jobId failed", e)

            val job = jobRepository.findById(jobId).orElse(null)
            if (job != null) {
                job.status = JobStatus.FAILED
                job.completedAt = Instant.now()
                job.errorMessage = e.message
                jobRepository.save(job)
            }
        } finally {
            zipFile.delete()
        }
    }

    private fun countXmlEntries(zipFile: File): Int {
        var count = 0
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".xml")) {
                    count++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return count
    }

    private fun processXmlEntry(jobId: UUID, xmlFileName: String, xmlBytes: ByteArray, writer: PrintWriter) {
        val extractionResult = nfeXmlExtractor.extract(xmlBytes.inputStream(), xmlFileName)

        if (extractionResult.isFailure) {
            val error = extractionResult.exceptionOrNull()?.message ?: "Unknown error"

            jobProgressRepository.save(
                JobProgressEntity(
                    jobId = jobId,
                    xmlFileName = xmlFileName,
                    success = false,
                    errorMessage = error
                )
            )

            jobAlertRepository.save(
                JobAlertEntity(
                    jobId = jobId,
                    xmlFileName = xmlFileName,
                    severity = AlertSeverity.ERROR,
                    message = "Failed to parse XML: $error"
                )
            )

            writer.println("${escapeCsv(xmlFileName)},,,,,,,,,,ERROR: $error")
            return
        }

        val nfeData = extractionResult.getOrThrow()
        val validationResult = nfeValidationCore.validate(nfeData, xmlFileName)

        jobProgressRepository.save(
            JobProgressEntity(
                jobId = jobId,
                xmlFileName = xmlFileName,
                success = validationResult.errors.isEmpty()
            )
        )

        for (error in validationResult.errors) {
            jobAlertRepository.save(
                JobAlertEntity(
                    jobId = jobId,
                    xmlFileName = xmlFileName,
                    severity = AlertSeverity.ERROR,
                    message = error
                )
            )
        }

        for (warning in validationResult.warnings) {
            jobAlertRepository.save(
                JobAlertEntity(
                    jobId = jobId,
                    xmlFileName = xmlFileName,
                    severity = AlertSeverity.WARNING,
                    message = warning
                )
            )
        }

        for (item in nfeData.items) {
            val itemKey = "${item.nItem}-${item.cProd}"
            val suggestion = validationResult.ncmSuggestions[itemKey]

            // Determinar status e observação
            val (status, observacao) = when {
                suggestion == null -> "ERROR" to "NCM não processado"
                !suggestion.valido && suggestion.sugestao != null ->
                    "NCM_INVALIDO" to "NCM não existe na base. Sugestão: ${suggestion.sugestao}"
                !suggestion.valido ->
                    "NCM_INVALIDO" to "NCM não existe na base"
                !suggestion.descricaoCompativel && suggestion.sugestao != null ->
                    "DESCRICAO_INCOMPATIVEL" to "Descrição do produto não corresponde ao NCM. Similaridade: ${String.format("%.1f", (suggestion.similaridade ?: 0.0) * 100)}%. Sugestão: ${suggestion.sugestao}"
                !suggestion.descricaoCompativel ->
                    "DESCRICAO_INCOMPATIVEL" to "Descrição do produto não corresponde ao NCM. Similaridade: ${String.format("%.1f", (suggestion.similaridade ?: 0.0) * 100)}%"
                else -> "OK" to null
            }

            val row = CsvResultRow(
                xmlFileName = xmlFileName,
                chaveNfe = nfeData.chave,
                numeroNfe = nfeData.numero,
                nItem = item.nItem,
                cProd = item.cProd,
                xProd = item.xProd,
                ncmOriginal = item.ncm,
                ncmSanitizado = textNormalizer.sanitizeNcm(item.ncm),
                ncmValido = suggestion?.valido ?: false,
                descricaoNcmOficial = suggestion?.descricaoSugestao,
                descricaoCompativel = suggestion?.descricaoCompativel ?: false,
                ncmSugerido = suggestion?.sugestao,
                similaridade = suggestion?.similaridade,
                status = status,
                observacao = observacao
            )

            writer.println(toCsvLine(row))

            if (suggestion != null && !suggestion.valido && suggestion.sugestao != null) {
                jobAlertRepository.save(
                    JobAlertEntity(
                        jobId = jobId,
                        xmlFileName = xmlFileName,
                        severity = AlertSeverity.INFO,
                        message = "NCM suggestion for item ${item.nItem}",
                        ncmOriginal = item.ncm,
                        ncmSugerido = suggestion.sugestao,
                        similaridade = suggestion.similaridade
                    )
                )
            }
        }
    }

    private fun toCsvLine(row: CsvResultRow): String {
        return listOf(
            escapeCsv(row.xmlFileName),
            escapeCsv(row.chaveNfe),
            escapeCsv(row.numeroNfe),
            row.nItem.toString(),
            escapeCsv(row.cProd),
            escapeCsv(row.xProd),
            escapeCsv(row.ncmOriginal),
            escapeCsv(row.ncmSanitizado),
            row.ncmValido.toString(),
            escapeCsv(row.descricaoNcmOficial ?: ""),
            row.descricaoCompativel.toString(),
            escapeCsv(row.ncmSugerido ?: ""),
            row.similaridade?.let { String.format("%.4f", it) } ?: "",
            row.status,
            escapeCsv(row.observacao ?: "")
        ).joinToString(",")
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    fun getJob(id: UUID): JobDetailResponse {
        val job = jobRepository.findById(id).orElseThrow {
            NoSuchElementException("Job not found: $id")
        }

        val alerts = jobAlertRepository.findByJobId(id).map { alert ->
            JobAlertResponse(
                id = alert.id,
                xmlFileName = alert.xmlFileName,
                severity = alert.severity,
                message = alert.message,
                createdAt = alert.createdAt,
                ncmOriginal = alert.ncmOriginal,
                ncmSugerido = alert.ncmSugerido,
                similaridade = alert.similaridade
            )
        }

        val successCount = jobProgressRepository.countByJobIdAndSuccess(id, true)
        val errorCount = jobProgressRepository.countByJobIdAndSuccess(id, false)

        val progress = if (job.totalXmlFiles > 0) {
            job.processedXmlFiles.toDouble() / job.totalXmlFiles.toDouble()
        } else {
            0.0
        }

        return JobDetailResponse(
            job = JobResponse(
                id = job.id,
                fileName = job.fileName,
                status = job.status,
                createdAt = job.createdAt,
                startedAt = job.startedAt,
                completedAt = job.completedAt,
                totalXmlFiles = job.totalXmlFiles,
                processedXmlFiles = job.processedXmlFiles,
                errorMessage = job.errorMessage,
                progress = progress
            ),
            alerts = alerts,
            successCount = successCount,
            errorCount = errorCount
        )
    }

    fun getResultFile(id: UUID): File {
        val job = jobRepository.findById(id).orElseThrow {
            NoSuchElementException("Job not found: $id")
        }

        if (job.status != JobStatus.COMPLETED) {
            throw IllegalStateException("Job is not completed yet. Current status: ${job.status}")
        }

        val resultFilePath = job.resultFilePath
            ?: throw IllegalStateException("Result file path not set for job: $id")

        val file = File(resultFilePath)
        if (!file.exists()) {
            throw NoSuchElementException("Result file not found: $resultFilePath")
        }

        return file
    }
}
