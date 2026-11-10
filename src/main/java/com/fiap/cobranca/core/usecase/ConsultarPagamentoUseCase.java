package com.fiap.cobranca.core.usecase;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.exception.PagamentoNaoEncontradoException;
import com.fiap.cobranca.core.gateway.PagamentoGateway;

public class ConsultarPagamentoUseCase {

    private final PagamentoGateway pagamentoGateway;

    public ConsultarPagamentoUseCase(PagamentoGateway pagamentoGateway) {
        this.pagamentoGateway = pagamentoGateway;
    }

    public Pagamento executar(Long ordemServicoId) {
        return pagamentoGateway.buscar(ordemServicoId)
                .orElseThrow(() -> new PagamentoNaoEncontradoException(ordemServicoId));
    }
}
