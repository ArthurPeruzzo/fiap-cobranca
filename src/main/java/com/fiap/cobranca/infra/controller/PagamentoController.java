package com.fiap.cobranca.infra.controller;

import com.fiap.cobranca.core.usecase.ConsultarPagamentoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pagamentos")
@Tag(name = "Pagamento")
public class PagamentoController {

    private final ConsultarPagamentoUseCase consultar;

    public PagamentoController(ConsultarPagamentoUseCase consultar) {
        this.consultar = consultar;
    }

    @GetMapping("/{ordemServicoId}")
    @Operation(summary = "Consulta o pagamento de uma ordem de serviço")
    public PagamentoResponseJson consultar(@PathVariable Long ordemServicoId) {
        return PagamentoResponseJson.de(consultar.executar(ordemServicoId));
    }
}
