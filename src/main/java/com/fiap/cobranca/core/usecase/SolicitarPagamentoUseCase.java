package com.fiap.cobranca.core.usecase;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.dto.SolicitarPagamentoDto;
import com.fiap.cobranca.core.gateway.GatewayDePagamento;
import com.fiap.cobranca.core.gateway.PagamentoGateway;

import java.time.Clock;

/** Registra o pagamento PENDENTE da OS e o inicia no meio de pagamento. Idempotente: repetir não cria outro. */
public class SolicitarPagamentoUseCase {

    private final PagamentoGateway pagamentoGateway;
    private final GatewayDePagamento gatewayDePagamento;
    private final Clock clock;

    public SolicitarPagamentoUseCase(PagamentoGateway pagamentoGateway, GatewayDePagamento gatewayDePagamento, Clock clock) {
        this.pagamentoGateway = pagamentoGateway;
        this.gatewayDePagamento = gatewayDePagamento;
        this.clock = clock;
    }

    /** @return true se criou o pagamento; false se já existia (mensagem duplicada). */
    public boolean solicitar(SolicitarPagamentoDto dto) {
        Pagamento pagamento = Pagamento.novo(dto.ordemServicoId(), dto.clienteId(), dto.valor(), clock.instant());
        if (!pagamentoGateway.criarSeNaoExistir(pagamento)) {
            return false;
        }
        gatewayDePagamento.iniciarCobranca(pagamento);
        return true;
    }
}
