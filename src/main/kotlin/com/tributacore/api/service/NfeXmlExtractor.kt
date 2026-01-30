package com.tributacore.api.service

import com.tributacore.api.dto.NfeData
import com.tributacore.api.dto.NfeItem
import org.springframework.stereotype.Component
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

@Component
class NfeXmlExtractor {

    companion object {
        private const val MAX_XML_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
        private val NFE_NAMESPACES = listOf(
            "http://www.portalfiscal.inf.br/nfe",
            ""
        )
    }

    fun extract(inputStream: InputStream, xmlFileName: String): Result<NfeData> {
        return try {
            val bytes = inputStream.readBytes()

            if (bytes.size > MAX_XML_SIZE_BYTES) {
                return Result.failure(IllegalArgumentException("XML file exceeds maximum size of 10MB"))
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
        } catch (e: Exception) {
            Result.failure(e)
        }
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
