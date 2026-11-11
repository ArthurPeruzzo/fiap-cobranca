package com.fiap.cobranca.infra.controller;

import com.fiap.cobranca.core.gateway.GatewayDePagamento;
import com.fiap.cobranca.core.gateway.PagamentoEventosGateway;
import com.fiap.cobranca.core.gateway.PagamentoGateway;
import com.fiap.cobranca.infra.controller.json.PagamentoResponseJson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@RestController
@RequestMapping("/pagamentos")
@Tag(name = "Pagamento")
public class PagamentoHttpController {

    private final PagamentoCleanController cleanController;

    public PagamentoHttpController(PagamentoGateway pagamentoGateway, PagamentoEventosGateway eventosGateway,
                                   GatewayDePagamento gatewayDePagamento, Clock clock) {
        this.cleanController = new PagamentoCleanController(pagamentoGateway, eventosGateway, gatewayDePagamento, clock);
    }

    @GetMapping("/{ordemServicoId}")
    @Operation(summary = "Consulta o pagamento de uma ordem de serviço",
            description = "Retorna o pagamento (valor e status PENDENTE, CONFIRMADO ou RECUSADO) da ordem de serviço informada")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pagamento retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Pagamento da ordem de serviço não encontrado")
    })
    public ResponseEntity<PagamentoResponseJson> consultar(@PathVariable Long ordemServicoId) {
        return ResponseEntity.ok(cleanController.consultar(ordemServicoId));
    }
}
