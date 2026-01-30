package com.tributacore.api.controller

import com.tributacore.api.domain.NcmEntity
import com.tributacore.api.repository.NcmRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/ncm")
class NcmController(
    private val ncmRepository: NcmRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(NcmController::class.java)

    @GetMapping("/count")
    fun count(): ResponseEntity<Map<String, Long>> {
        val count = ncmRepository.count()
        return ResponseEntity.ok(mapOf("count" to count))
    }

    @PostMapping("/upload")
    fun uploadNcmJson(@RequestParam("file") file: MultipartFile): ResponseEntity<Map<String, Any>> {
        logger.info("Receiving NCM JSON file: ${file.originalFilename}, size: ${file.size}")

        try {
            val jsonNode = objectMapper.readTree(file.inputStream)
            val ncmList = parseNcmJson(jsonNode)

            if (ncmList.isEmpty()) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "No NCM entries found in JSON file"
                ))
            }

            // Clear existing data
            ncmRepository.deleteAll()

            // Save in batches
            val batchSize = 1000
            var saved = 0

            ncmList.chunked(batchSize).forEach { batch ->
                ncmRepository.saveAll(batch)
                saved += batch.size
                logger.info("Saved $saved / ${ncmList.size} NCM entries")
            }

            return ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Successfully loaded ${ncmList.size} NCM entries",
                "count" to ncmList.size
            ))

        } catch (e: Exception) {
            logger.error("Failed to load NCM data", e)
            return ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to load NCM data: ${e.message}"
            ))
        }
    }

    private fun parseNcmJson(jsonNode: JsonNode): List<NcmEntity> {
        val ncmList = mutableListOf<NcmEntity>()

        when {
            jsonNode.isArray -> {
                jsonNode.forEach { item ->
                    parseNcmItem(item)?.let { ncmList.add(it) }
                }
            }
            jsonNode.has("Nomenclaturas") -> {
                jsonNode["Nomenclaturas"].forEach { item ->
                    parseNcmItem(item)?.let { ncmList.add(it) }
                }
            }
            jsonNode.has("data") -> {
                jsonNode["data"].forEach { item ->
                    parseNcmItem(item)?.let { ncmList.add(it) }
                }
            }
            jsonNode.has("ncm") -> {
                jsonNode["ncm"].forEach { item ->
                    parseNcmItem(item)?.let { ncmList.add(it) }
                }
            }
            else -> {
                logger.warn("Unknown JSON structure")
            }
        }

        return ncmList
    }

    private fun parseNcmItem(item: JsonNode): NcmEntity? {
        val codigo = item.findValue("Codigo")?.asText()
            ?: item.findValue("codigo")?.asText()
            ?: item.findValue("NCM")?.asText()
            ?: item.findValue("ncm")?.asText()
            ?: item.findValue("code")?.asText()
            ?: return null

        val descricao = item.findValue("Descricao")?.asText()
            ?: item.findValue("descricao")?.asText()
            ?: item.findValue("Descricao_NCM")?.asText()
            ?: item.findValue("description")?.asText()
            ?: item.findValue("nome")?.asText()
            ?: return null

        val codigoSanitizado = codigo.replace(Regex("[^0-9]"), "").take(8).padEnd(8, '0')

        if (codigoSanitizado.length != 8) {
            return null
        }

        return NcmEntity(
            codigo = codigoSanitizado,
            descricao = descricao.trim()
        )
    }

    @GetMapping("/search")
    fun search(@RequestParam("codigo") codigo: String): ResponseEntity<Any> {
        val ncm = ncmRepository.findById(codigo).orElse(null)
        return if (ncm != null) {
            ResponseEntity.ok(ncm)
        } else {
            ResponseEntity.notFound().build()
        }
    }

//    @GetMapping("/exists/{codigo}")
//    fun exists(@PathVariable codigo: String): ResponseEntity<Map<String, Any>> {
//        val codigoSanitizado = codigo.replace(Regex("[^0-9]"), "").take(8).padEnd(8, '0')
//        val exists = ncmRepository.existsById(codigoSanitizado)
//
//        return ResponseEntity.ok(mapOf(
//            "codigo" to codigoSanitizado,
//            "exists" to exists
//        ))
//    }
}
