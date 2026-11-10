package com.fiap.cobranca.infra.controller;

import com.fiap.cobranca.core.usecase.DecidirPagamentoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Decisão manual do pagamento, no lugar do meio de pagamento real. Protegido por JWT (ATENDENTE ou ADMINISTRADOR) e
 * desligável por {@code cobranca.pagamento.simulado.enabled=false}, o que remove os endpoints.
 */
@RestController
@RequestMapping("/pagamentos")
@Tag(name = "Pagamento")
@ConditionalOnProperty(name = "cobranca.pagamento.simulado.enabled", havingValue = "true", matchIfMissing = true)
public class PagamentoSimuladoController {

    private final DecidirPagamentoUseCase decidir;

    public PagamentoSimuladoController(DecidirPagamentoUseCase decidir) {
        this.decidir = decidir;
    }

    @PostMapping("/{ordemServicoId}/aprovar")
    @Operation(summary = "Aprova o pagamento (simulado) e avisa a ordem de serviço")
    public PagamentoResponseJson aprovar(@PathVariable Long ordemServicoId) {
        return PagamentoResponseJson.de(decidir.confirmar(ordemServicoId));
    }

    @PostMapping("/{ordemServicoId}/recusar")
    @Operation(summary = "Recusa o pagamento (simulado) e avisa a ordem de serviço")
    public PagamentoResponseJson recusar(@PathVariable Long ordemServicoId) {
        return PagamentoResponseJson.de(decidir.recusar(ordemServicoId));
    }
}
