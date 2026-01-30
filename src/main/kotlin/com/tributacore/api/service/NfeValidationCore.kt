package com.tributacore.api.service

import com.tributacore.api.dto.NcmSuggestion
import com.tributacore.api.dto.NfeData
import com.tributacore.api.dto.ValidationResult
import org.springframework.stereotype.Component

@Component
class NfeValidationCore(
    private val ncmCompatibilityService: NcmCompatibilityService,
    private val textNormalizer: TextNormalizer
) {

    fun validate(nfeData: NfeData, xmlFileName: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val ncmSuggestions = mutableMapOf<String, NcmSuggestion>()

        if (nfeData.chave.isBlank()) {
            errors.add("Chave da NF-e não encontrada")
        } else if (nfeData.chave.length != 44) {
            warnings.add("Chave da NF-e com tamanho inválido: ${nfeData.chave.length} caracteres (esperado: 44)")
        }

        if (nfeData.emitenteCnpj.isBlank()) {
            errors.add("CNPJ do emitente não encontrado")
        }

        if (nfeData.items.isEmpty()) {
            errors.add("Nenhum item encontrado na NF-e")
        }

        for (item in nfeData.items) {
            val itemKey = "${item.nItem}-${item.cProd}"

            if (item.ncm.isBlank()) {
                errors.add("Item $itemKey: NCM não informado")
                continue
            }

            if (!textNormalizer.isValidNcmFormat(item.ncm)) {
                warnings.add("Item $itemKey: NCM com formato inválido: ${item.ncm}")
            }

            val suggestion = ncmCompatibilityService.validateAndSuggest(item.ncm, item.xProd)
            ncmSuggestions[itemKey] = suggestion

            if (!suggestion.valido) {
                if (suggestion.sugestao != null) {
                    warnings.add(
                        "Item $itemKey: NCM ${item.ncm} não encontrado. " +
                        "Sugestão: ${suggestion.sugestao} (${suggestion.descricaoSugestao}) " +
                        "com similaridade de ${String.format("%.2f", (suggestion.similaridade ?: 0.0) * 100)}%"
                    )
                } else {
                    warnings.add("Item $itemKey: NCM ${item.ncm} não encontrado e sem sugestão disponível")
                }
            } else if (!suggestion.descricaoCompativel) {
                // NCM existe mas descrição do produto não é compatível
                val similarityPercent = String.format("%.2f", (suggestion.similaridade ?: 0.0) * 100)
                if (suggestion.sugestao != null) {
                    warnings.add(
                        "Item $itemKey: Descrição do produto '${item.xProd}' tem baixa similaridade ($similarityPercent%) " +
                        "com a descrição oficial do NCM ${suggestion.ncmSanitizado} ('${suggestion.descricaoSugestao}'). " +
                        "Sugestão alternativa: ${suggestion.sugestao}"
                    )
                } else {
                    warnings.add(
                        "Item $itemKey: Descrição do produto '${item.xProd}' tem baixa similaridade ($similarityPercent%) " +
                        "com a descrição oficial do NCM ${suggestion.ncmSanitizado} ('${suggestion.descricaoSugestao}'). " +
                        "Verifique se o NCM está correto."
                    )
                }
            }

            if (item.xProd.isBlank()) {
                warnings.add("Item $itemKey: Descrição do produto vazia")
            }

            if (item.vProd <= 0) {
                warnings.add("Item $itemKey: Valor do produto zerado ou negativo")
            }
        }

        return ValidationResult(
            xmlFileName = xmlFileName,
            nfeData = nfeData,
            errors = errors,
            warnings = warnings,
            ncmSuggestions = ncmSuggestions
        )
    }
}
