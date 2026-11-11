package com.fiap.cobranca.infra.controller;

import com.fiap.cobranca.core.gateway.GatewayDePagamento;
import com.fiap.cobranca.core.gateway.PagamentoEventosGateway;
import com.fiap.cobranca.core.gateway.PagamentoGateway;
import com.fiap.cobranca.infra.controller.json.PagamentoResponseJson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/**
 * Decisão manual do pagamento, no lugar do meio de pagamento real. Protegido por JWT (ATENDENTE ou ADMINISTRADOR) e
 * desligável por {@code cobranca.pagamento.simulado.enabled=false}, o que remove os endpoints.
 */
@RestController
@RequestMapping("/pagamentos")
@Tag(name = "Pagamento")
@ConditionalOnProperty(name = "cobranca.pagamento.simulado.enabled", havingValue = "true", matchIfMissing = true)
public class PagamentoSimuladoHttpController {

    private final PagamentoCleanController cleanController;

    public PagamentoSimuladoHttpController(PagamentoGateway pagamentoGateway, PagamentoEventosGateway eventosGateway,
                                           GatewayDePagamento gatewayDePagamento, Clock clock) {
        this.cleanController = new PagamentoCleanController(pagamentoGateway, eventosGateway, gatewayDePagamento, clock);
    }

    @PostMapping("/{ordemServicoId}/aprovar")
    @Operation(summary = "Aprova o pagamento (simulado) e avisa a ordem de serviço",
            description = "Confirma o pagamento PENDENTE e publica a resposta para a ordem de serviço. Repetir a aprovação reenvia a resposta")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pagamento confirmado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado (apenas ATENDENTE ou ADMINISTRADOR)"),
            @ApiResponse(responseCode = "404", description = "Pagamento da ordem de serviço não encontrado"),
            @ApiResponse(responseCode = "409", description = "O pagamento já foi recusado")
    })
    public ResponseEntity<PagamentoResponseJson> aprovar(@PathVariable Long ordemServicoId) {
        return ResponseEntity.ok(cleanController.confirmar(ordemServicoId));
    }

    @PostMapping("/{ordemServicoId}/recusar")
    @Operation(summary = "Recusa o pagamento (simulado) e avisa a ordem de serviço",
            description = "Recusa o pagamento PENDENTE e publica a resposta para a ordem de serviço, que é cancelada. Repetir a recusa reenvia a resposta")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pagamento recusado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado (apenas ATENDENTE ou ADMINISTRADOR)"),
            @ApiResponse(responseCode = "404", description = "Pagamento da ordem de serviço não encontrado"),
            @ApiResponse(responseCode = "409", description = "O pagamento já foi confirmado")
    })
    public ResponseEntity<PagamentoResponseJson> recusar(@PathVariable Long ordemServicoId) {
        return ResponseEntity.ok(cleanController.recusar(ordemServicoId));
    }
}
