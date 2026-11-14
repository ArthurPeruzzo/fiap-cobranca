package com.fiap.cobranca.core.usecase;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.dto.SolicitarPagamentoDto;
import com.fiap.cobranca.core.gateway.GatewayDePagamento;
import com.fiap.cobranca.core.gateway.PagamentoEventosGateway;
import com.fiap.cobranca.core.gateway.PagamentoGateway;

import java.time.Clock;

/**
 * Registra o pagamento PENDENTE da OS e o inicia no meio de pagamento. Idempotente: repetir não cria outro.
 * <p>
 * Se o meio já responde com a decisão (aprovado/recusado), ela segue pelo mesmo {@link DecidirPagamentoUseCase} da
 * decisão manual, que grava o status e avisa a OS. {@code PENDENTE} deixa a decisão para depois.
 */
public class SolicitarPagamentoUseCase {

    private final PagamentoGateway pagamentoGateway;
    private final GatewayDePagamento gatewayDePagamento;
    private final DecidirPagamentoUseCase decidirPagamento;
    private final Clock clock;

    public SolicitarPagamentoUseCase(PagamentoGateway pagamentoGateway, PagamentoEventosGateway eventosGateway,
                                     GatewayDePagamento gatewayDePagamento, Clock clock) {
        this.pagamentoGateway = pagamentoGateway;
        this.gatewayDePagamento = gatewayDePagamento;
        this.decidirPagamento = new DecidirPagamentoUseCase(pagamentoGateway, eventosGateway, clock, pagamento -> { });
        this.clock = clock;
    }

    /** @return true se criou o pagamento; false se já existia (mensagem duplicada). */
    public boolean solicitar(SolicitarPagamentoDto dto) {
        Pagamento pagamento = Pagamento.novo(dto.ordemServicoId(), dto.clienteId(), dto.valor(), clock.instant());
        if (!pagamentoGateway.criarSeNaoExistir(pagamento)) {
            return false;
        }
        switch (gatewayDePagamento.iniciarCobranca(pagamento)) {
            case APROVADO -> decidirPagamento.confirmar(pagamento.ordemServicoId());
            case RECUSADO -> decidirPagamento.recusar(pagamento.ordemServicoId());
            case PENDENTE -> { /* a decisão chega depois (manual ou webhook) */ }
        }
        return true;
    }
}
