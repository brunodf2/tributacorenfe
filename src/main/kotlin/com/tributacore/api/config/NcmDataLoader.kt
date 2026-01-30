package com.tributacore.api.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.tributacore.api.domain.NcmEntity
import com.tributacore.api.repository.NcmRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.File

@Component
class NcmDataLoader(
    private val ncmRepository: NcmRepository,
    private val objectMapper: ObjectMapper
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(NcmDataLoader::class.java)

    @Value("\${tributacore.ncm.json-path:Tabela_NCM_Vigente_20260127.json}")
    private lateinit var ncmJsonPath: String

    @Value("\${tributacore.ncm.force-reload:false}")
    private var forceReload: Boolean = false

    @Transactional
    override fun run(vararg args: String?) {
        val count = ncmRepository.count()

        if (count > 0 && !forceReload) {
            logger.info("NCM table already populated with $count entries. Skipping load.")
            return
        }

        val file = File(ncmJsonPath)
        if (!file.exists()) {
            logger.warn("NCM JSON file not found at: ${file.absolutePath}. Skipping NCM load.")
            return
        }

        logger.info("Loading NCM data from: ${file.absolutePath}")

        try {
            val jsonNode = objectMapper.readTree(file)
            val ncmList = parseNcmJson(jsonNode)

            if (ncmList.isEmpty()) {
                logger.warn("No NCM entries found in JSON file")
                return
            }

            if (forceReload) {
                logger.info("Force reload enabled. Clearing existing NCM data...")
                ncmRepository.deleteAll()
            }

            // Save in batches for better performance
            val batchSize = 1000
            var saved = 0

            ncmList.chunked(batchSize).forEach { batch ->
                ncmRepository.saveAll(batch)
                saved += batch.size
                logger.info("Saved $saved / ${ncmList.size} NCM entries")
            }

            logger.info("Successfully loaded ${ncmList.size} NCM entries")

        } catch (e: Exception) {
            logger.error("Failed to load NCM data: ${e.message}", e)
        }
    }

    private fun parseNcmJson(jsonNode: JsonNode): List<NcmEntity> {
        val ncmList = mutableListOf<NcmEntity>()

        // Try different JSON structures
        when {
            // Array format: [{"codigo": "...", "descricao": "..."}, ...]
            jsonNode.isArray -> {
                jsonNode.forEach { item ->
                    parseNcmItem(item)?.let { ncmList.add(it) }
                }
            }
            // Object with "Nomenclaturas" array (common format from government)
            jsonNode.has("Nomenclaturas") -> {
                jsonNode["Nomenclaturas"].forEach { item ->
                    parseNcmItem(item)?.let { ncmList.add(it) }
                }
            }
            // Object with "data" array
            jsonNode.has("data") -> {
                jsonNode["data"].forEach { item ->
                    parseNcmItem(item)?.let { ncmList.add(it) }
                }
            }
            // Object with "ncm" array
            jsonNode.has("ncm") -> {
                jsonNode["ncm"].forEach { item ->
                    parseNcmItem(item)?.let { ncmList.add(it) }
                }
            }
            else -> {
                logger.warn("Unknown JSON structure. Expected array or object with 'Nomenclaturas', 'data', or 'ncm' field")
            }
        }

        return ncmList
    }

    private fun parseNcmItem(item: JsonNode): NcmEntity? {
        // Try different field names for codigo
        val codigo = item.findValue("Codigo")?.asText()
            ?: item.findValue("codigo")?.asText()
            ?: item.findValue("NCM")?.asText()
            ?: item.findValue("ncm")?.asText()
            ?: item.findValue("code")?.asText()
            ?: return null

        // Try different field names for descricao
        val descricao = item.findValue("Descricao")?.asText()
            ?: item.findValue("descricao")?.asText()
            ?: item.findValue("Descricao_NCM")?.asText()
            ?: item.findValue("description")?.asText()
            ?: item.findValue("nome")?.asText()
            ?: return null

        // Sanitize codigo to 8 digits
        val codigoSanitizado = codigo.replace(Regex("[^0-9]"), "").take(8).padEnd(8, '0')

        if (codigoSanitizado.length != 8) {
            return null
        }

        return NcmEntity(
            codigo = codigoSanitizado,
            descricao = descricao.trim()
        )
    }
}
