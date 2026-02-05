package com.tributacore.api.dto

import com.tributacore.api.domain.AlertSeverity
import com.tributacore.api.domain.JobStatus
import java.time.Instant
import java.util.*

data class JobResponse(
    val id: UUID,
    val fileName: String,
    val status: JobStatus,
    val createdAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val totalXmlFiles: Int,
    val processedXmlFiles: Int,
    val errorMessage: String?,
    val progress: Double
)

data class JobCreateResponse(
    val id: UUID,
    val message: String
)

data class JobAlertResponse(
    val id: Long?,
    val xmlFileName: String,
    val severity: AlertSeverity,
    val message: String,
    val createdAt: Instant,
    val ncmOriginal: String?,
    val ncmSugerido: String?,
    val similaridade: Double?
)

data class JobDetailResponse(
    val job: JobResponse,
    val alerts: List<JobAlertResponse>,
    val successCount: Long,
    val errorCount: Long
)

data class NfeItem(
    val nItem: Int,
    val cProd: String,
    val xProd: String,
    val ncm: String,
    val cfop: String,
    val uCom: String,
    val qCom: Double,
    val vUnCom: Double,
    val vProd: Double
)

data class NfeData(
    val chave: String,
    val numero: String,
    val serie: String,
    val dhEmi: String,
    val emitenteCnpj: String,
    val emitenteNome: String,
    val destinatarioCnpj: String?,
    val destinatarioNome: String?,
    val items: List<NfeItem>
)

data class ValidationResult(
    val xmlFileName: String,
    val nfeData: NfeData?,
    val errors: List<String>,
    val warnings: List<String>,
    val ncmSuggestions: Map<String, NcmSuggestion>
)

data class NcmSuggestion(
    val ncmOriginal: String,
    val ncmSanitizado: String,
    val valido: Boolean,
    val sugestao: String?,
    val descricaoSugestao: String?,
    val similaridade: Double?,
    val descricaoCompativel: Boolean = true,
    val ncmSugeridoDescricao: String? = null,
    val ncmSugeridoSimilaridade: Double? = null,
    val mapeamentoCustomizado: Boolean = false // Indica se veio de mapeamento manual
)

// DTOs para Mapeamento Produto-NCM
data class ProdutoNcmMapeamentoRequest(
    val descricaoProduto: String,
    val ncmCodigo: String
)

data class ProdutoNcmMapeamentoResponse(
    val id: Long,
    val descricaoProduto: String,
    val descricaoNormalizada: String,
    val ncmCodigo: String,
    val ncmDescricao: String,  // Vem direto da tabela NCM
    val ativo: Boolean,
    val criadoEm: java.time.Instant,
    val atualizadoEm: java.time.Instant
)

data class ProdutoNcmMapeamentoBulkRequest(
    val mapeamentos: List<ProdutoNcmMapeamentoRequest>
)

data class ProdutoNcmMapeamentoBulkResponse(
    val criados: Int,
    val erros: List<String>
)

data class CsvResultRow(
    val xmlFileName: String,
    val chaveNfe: String,
    val numeroNfe: String,
    val nItem: Int,
    val cProd: String,
    val xProd: String,
    val ncmOriginal: String,
    val ncmSanitizado: String,
    val ncmValido: Boolean,
    val descricaoNcmOficial: String?,
    val descricaoCompativel: Boolean,
    val ncmSugerido: String?,
    val ncmSugeridoDescricao: String?,
    val ncmSugeridoSimilaridade: Double?,
    val similaridade: Double?,
    val mapeamentoCustomizado: Boolean,
    val status: String,
    val observacao: String?
)
