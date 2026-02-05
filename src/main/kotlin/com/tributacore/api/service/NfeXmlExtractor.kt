package com.tributacore.api.service

import com.tributacore.api.dto.NfeData
import com.tributacore.api.dto.NfeItem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.InputStream
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory

@Component
class NfeXmlExtractor {

    private val logger = LoggerFactory.getLogger(NfeXmlExtractor::class.java)

    companion object {
        private const val MAX_XML_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
        private val NFE_NAMESPACES = listOf(
            "http://www.portalfiscal.inf.br/nfe",
            ""
        )
    }

    fun extract(inputStream: InputStream, xmlFileName: String): Result<NfeData> {
        return try {
            var bytes = inputStream.readBytes()

            if (bytes.isEmpty()) {
                return Result.failure(IllegalArgumentException("Arquivo XML vazio"))
            }

            if (bytes.size > MAX_XML_SIZE_BYTES) {
                return Result.failure(IllegalArgumentException("XML file exceeds maximum size of 10MB"))
            }

            // Remover BOM (Byte Order Mark) se presente
            bytes = removeBom(bytes)

            // Verificar se o conteúdo parece ser XML válido
            val contentPreview = String(bytes.take(1000).toByteArray(), StandardCharsets.UTF_8).trim()

            if (!isValidXmlContent(contentPreview)) {
                logger.warn("Arquivo $xmlFileName não parece ser um XML válido. Início do conteúdo: ${contentPreview.take(100)}")
                return Result.failure(IllegalArgumentException("Arquivo não é um XML válido. O conteúdo não inicia com declaração XML ou tag de abertura."))
            }

            // Verificar se é um arquivo de EVENTO (não é NFe)
            if (isEventoNFe(xmlFileName, contentPreview)) {
                logger.info("Arquivo $xmlFileName é um evento de NFe (não é uma NFe). Ignorando.")
                return Result.failure(IllegalArgumentException("EVENTO_NFE: Arquivo é um evento de NFe (cancelamento, carta de correção, etc.), não uma NFe."))
            }

            // Verificar se parece ser uma NFe
            if (!contentPreview.contains("infNFe", ignoreCase = true)) {
                logger.warn("Arquivo $xmlFileName não parece ser uma NFe (não contém infNFe)")
                return Result.failure(IllegalArgumentException("Arquivo XML não parece ser uma NFe válida (elemento infNFe não encontrado)"))
            }

            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }

            val document = factory.newDocumentBuilder().parse(bytes.inputStream())
            document.documentElement.normalize()

            val nfeData = parseNfeDocument(document, xmlFileName)
            Result.success(nfeData)
        } catch (e: org.xml.sax.SAXParseException) {
            logger.error("Erro de parse XML em $xmlFileName: ${e.message}")
            Result.failure(IllegalArgumentException("Erro ao processar XML: ${e.message}. Verifique se o arquivo é um XML de NFe válido."))
        } catch (e: Exception) {
            logger.error("Erro ao processar $xmlFileName", e)
            Result.failure(e)
        }
    }

    /**
     * Verifica se o arquivo é um Evento de NFe (cancelamento, carta de correção, etc.)
     * Esses arquivos não contêm dados de NFe e devem ser ignorados
     */
    private fun isEventoNFe(fileName: String, contentPreview: String): Boolean {
        // Verificar pelo nome do arquivo
        val lowerFileName = fileName.lowercase()
        if (lowerFileName.contains("proceventonfe") ||
            lowerFileName.contains("proc_evento") ||
            lowerFileName.contains("-evento") ||
            lowerFileName.endsWith("_evento.xml")) {
            return true
        }

        // Verificar pelo conteúdo do XML
        val lowerContent = contentPreview.lowercase()
        if (lowerContent.contains("<proceventonfe") ||
            lowerContent.contains("<eventonfe") ||
            lowerContent.contains("<infevento") ||
            lowerContent.contains("<retevento") ||
            (lowerContent.contains("tpevento") && !lowerContent.contains("infnfe"))) {
            return true
        }

        return false
    }

    /**
     * Remove BOM (Byte Order Mark) do início do arquivo se presente
     */
    private fun removeBom(bytes: ByteArray): ByteArray {
        // UTF-8 BOM: EF BB BF
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()) {
            return bytes.drop(3).toByteArray()
        }
        // UTF-16 LE BOM: FF FE
        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte()) {
            return bytes.drop(2).toByteArray()
        }
        // UTF-16 BE BOM: FE FF
        if (bytes.size >= 2 &&
            bytes[0] == 0xFE.toByte() &&
            bytes[1] == 0xFF.toByte()) {
            return bytes.drop(2).toByteArray()
        }
        return bytes
    }

    /**
     * Verifica se o conteúdo parece ser um XML válido
     */
    private fun isValidXmlContent(content: String): Boolean {
        val trimmed = content.trimStart()

        // XML deve começar com declaração XML ou tag de abertura
        return trimmed.startsWith("<?xml") ||
               trimmed.startsWith("<") && !trimmed.startsWith("<!") // Ignora comentários e DOCTYPE no início
    }

    private fun parseNfeDocument(document: Document, xmlFileName: String): NfeData {
        val infNfe = findElement(document, "infNFe")
            ?: throw IllegalArgumentException("Element infNFe not found in XML")

        val chave = infNfe.getAttribute("Id")?.removePrefix("NFe") ?: ""

        val ide = findElement(document, "ide")
        val emit = findElement(document, "emit")
        val dest = findElement(document, "dest")

        val items = parseItems(document)

        return NfeData(
            chave = chave,
            numero = getElementText(ide, "nNF") ?: "",
            serie = getElementText(ide, "serie") ?: "",
            dhEmi = getElementText(ide, "dhEmi") ?: getElementText(ide, "dEmi") ?: "",
            emitenteCnpj = getElementText(emit, "CNPJ") ?: "",
            emitenteNome = getElementText(emit, "xNome") ?: "",
            destinatarioCnpj = getElementText(dest, "CNPJ") ?: getElementText(dest, "CPF"),
            destinatarioNome = getElementText(dest, "xNome"),
            items = items
        )
    }

    private fun parseItems(document: Document): List<NfeItem> {
        val items = mutableListOf<NfeItem>()
        val detElements = document.getElementsByTagName("det")

        for (i in 0 until detElements.length) {
            val det = detElements.item(i) as? Element ?: continue
            val prod = findChildElement(det, "prod") ?: continue

            val nItem = det.getAttribute("nItem")?.toIntOrNull() ?: (i + 1)

            items.add(
                NfeItem(
                    nItem = nItem,
                    cProd = getElementText(prod, "cProd") ?: "",
                    xProd = getElementText(prod, "xProd") ?: "",
                    ncm = getElementText(prod, "NCM") ?: "",
                    cfop = getElementText(prod, "CFOP") ?: "",
                    uCom = getElementText(prod, "uCom") ?: "",
                    qCom = getElementText(prod, "qCom")?.toDoubleOrNull() ?: 0.0,
                    vUnCom = getElementText(prod, "vUnCom")?.toDoubleOrNull() ?: 0.0,
                    vProd = getElementText(prod, "vProd")?.toDoubleOrNull() ?: 0.0
                )
            )
        }

        return items
    }

    private fun findElement(document: Document, tagName: String): Element? {
        for (ns in NFE_NAMESPACES) {
            val elements = if (ns.isEmpty()) {
                document.getElementsByTagName(tagName)
            } else {
                document.getElementsByTagNameNS(ns, tagName)
            }
            if (elements.length > 0) {
                return elements.item(0) as? Element
            }
        }
        return null
    }

    private fun findChildElement(parent: Element, tagName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.localName == tagName) {
                return child
            }
        }
        return parent.getElementsByTagName(tagName).item(0) as? Element
    }

    private fun getElementText(parent: Element?, tagName: String): String? {
        if (parent == null) return null
        val elements = parent.getElementsByTagName(tagName)
        return if (elements.length > 0) {
            elements.item(0)?.textContent?.trim()
        } else null
    }
}
