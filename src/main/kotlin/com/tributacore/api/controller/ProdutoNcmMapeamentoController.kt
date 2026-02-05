package com.tributacore.api.controller

import com.tributacore.api.dto.*
import com.tributacore.api.service.ProdutoNcmMapeamentoService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/mapeamentos")
class ProdutoNcmMapeamentoController(
    private val mapeamentoService: ProdutoNcmMapeamentoService
) {

    /**
     * Lista todos os mapeamentos ativos
     * GET /api/mapeamentos
     */
    @GetMapping
    fun listarTodos(): ResponseEntity<List<ProdutoNcmMapeamentoResponse>> {
        val mapeamentos = mapeamentoService.listarTodos()
        return ResponseEntity.ok(mapeamentos)
    }

    /**
     * Busca mapeamentos por termo na descrição do produto
     * GET /api/mapeamentos/buscar?termo=coxao
     */
    @GetMapping("/buscar")
    fun buscarPorTermo(@RequestParam termo: String): ResponseEntity<List<ProdutoNcmMapeamentoResponse>> {
        val mapeamentos = mapeamentoService.buscarPorTermo(termo)
        return ResponseEntity.ok(mapeamentos)
    }

    /**
     * Lista mapeamentos por código NCM
     * GET /api/mapeamentos/ncm/02013000
     */
    @GetMapping("/ncm/{ncmCodigo}")
    fun listarPorNcm(@PathVariable ncmCodigo: String): ResponseEntity<List<ProdutoNcmMapeamentoResponse>> {
        val mapeamentos = mapeamentoService.listarPorNcm(ncmCodigo)
        return ResponseEntity.ok(mapeamentos)
    }

    /**
     * Cria um novo mapeamento
     * POST /api/mapeamentos
     * Body: { "descricaoProduto": "COXAO MOLE KG", "ncmCodigo": "02013000", "observacao": "Carne bovina" }
     */
    @PostMapping
    fun criar(@RequestBody request: ProdutoNcmMapeamentoRequest): ResponseEntity<ProdutoNcmMapeamentoResponse> {
        val mapeamento = mapeamentoService.criar(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapeamento)
    }

    /**
     * Cria múltiplos mapeamentos de uma vez
     * POST /api/mapeamentos/bulk
     * Body: { "mapeamentos": [{ "descricaoProduto": "...", "ncmCodigo": "..." }, ...] }
     */
    @PostMapping("/bulk")
    fun criarEmLote(@RequestBody request: ProdutoNcmMapeamentoBulkRequest): ResponseEntity<ProdutoNcmMapeamentoBulkResponse> {
        val (criados, erros) = mapeamentoService.criarEmLote(request.mapeamentos)
        return ResponseEntity.ok(ProdutoNcmMapeamentoBulkResponse(criados, erros))
    }

    /**
     * Atualiza um mapeamento existente
     * PUT /api/mapeamentos/{id}
     */
    @PutMapping("/{id}")
    fun atualizar(
        @PathVariable id: Long,
        @RequestBody request: ProdutoNcmMapeamentoRequest
    ): ResponseEntity<ProdutoNcmMapeamentoResponse> {
        val mapeamento = mapeamentoService.atualizar(id, request)
        return ResponseEntity.ok(mapeamento)
    }

    /**
     * Desativa um mapeamento (soft delete)
     * DELETE /api/mapeamentos/{id}
     */
    @DeleteMapping("/{id}")
    fun desativar(@PathVariable id: Long): ResponseEntity<Unit> {
        mapeamentoService.desativar(id)
        return ResponseEntity.noContent().build()
    }

    /**
     * Reativa um mapeamento
     * POST /api/mapeamentos/{id}/reativar
     */
    @PostMapping("/{id}/reativar")
    fun reativar(@PathVariable id: Long): ResponseEntity<ProdutoNcmMapeamentoResponse> {
        val mapeamento = mapeamentoService.reativar(id)
        return ResponseEntity.ok(mapeamento)
    }

    /**
     * Handler de exceções
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Erro de validação")))
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (e.message ?: "Não encontrado")))
    }
}
