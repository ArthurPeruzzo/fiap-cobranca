package com.fiap.cobranca.core.usecase;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.exception.PagamentoNaoEncontradoException;
import com.fiap.cobranca.core.gateway.PagamentoGateway;

public class ConsultarPagamentoUseCase {

    private final PagamentoGateway pagamentoGateway;
    private final ConsultarPagamentoOutputPort outputPort;

    public ConsultarPagamentoUseCase(PagamentoGateway pagamentoGateway, ConsultarPagamentoOutputPort outputPort) {
        this.pagamentoGateway = pagamentoGateway;
        this.outputPort = outputPort;
    }

    public void consultar(Long ordemServicoId) {
        Pagamento pagamento = pagamentoGateway.buscar(ordemServicoId)
                .orElseThrow(() -> new PagamentoNaoEncontradoException(ordemServicoId));
        outputPort.apresentar(pagamento);
    }
}
