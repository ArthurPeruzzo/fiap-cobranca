package com.fiap.cobranca.core.usecase;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.domain.StatusPagamento;
import com.fiap.cobranca.core.exception.PagamentoJaDecididoException;
import com.fiap.cobranca.core.exception.PagamentoNaoEncontradoException;
import com.fiap.cobranca.core.gateway.PagamentoEventosGateway;
import com.fiap.cobranca.core.gateway.PagamentoGateway;

import java.time.Clock;

/**
 * Decide o pagamento (confirmar ou recusar) e avisa o orquestrador. Quem decide não importa para a regra: o endpoint
 * simulado e, no futuro, o webhook do Mercado Pago chamam o mesmo caso de uso.
 * <p>
 * Repetir a mesma decisão é permitido e reenvia a resposta: se a publicação falhou depois de gravar o status, o
 * chamador tenta de novo e a resposta sai (risco aceito: gravação e publicação não são atômicas). Decisão contrária à
 * já gravada é conflito.
 */
public class DecidirPagamentoUseCase {

    private final PagamentoGateway pagamentoGateway;
    private final PagamentoEventosGateway eventosGateway;
    private final Clock clock;

    public DecidirPagamentoUseCase(PagamentoGateway pagamentoGateway, PagamentoEventosGateway eventosGateway, Clock clock) {
        this.pagamentoGateway = pagamentoGateway;
        this.eventosGateway = eventosGateway;
        this.clock = clock;
    }

    public Pagamento confirmar(Long ordemServicoId) {
        return decidir(ordemServicoId, StatusPagamento.CONFIRMADO);
    }

    public Pagamento recusar(Long ordemServicoId) {
        return decidir(ordemServicoId, StatusPagamento.RECUSADO);
    }

    private Pagamento decidir(Long ordemServicoId, StatusPagamento alvo) {
        Pagamento atual = pagamentoGateway.buscar(ordemServicoId)
                .orElseThrow(() -> new PagamentoNaoEncontradoException(ordemServicoId));

        if (atual.status() == StatusPagamento.PENDENTE) {
            // Escrita condicional: se outra decisão chegou antes, o resultado é ignorado e a releitura diz quem venceu.
            pagamentoGateway.transicionar(ordemServicoId, StatusPagamento.PENDENTE, alvo, clock.instant());
            atual = pagamentoGateway.buscar(ordemServicoId)
                    .orElseThrow(() -> new PagamentoNaoEncontradoException(ordemServicoId));
        }

        if (atual.status() != alvo) {
            throw new PagamentoJaDecididoException(ordemServicoId, atual.status());
        }

        if (alvo == StatusPagamento.CONFIRMADO) {
            eventosGateway.pagamentoConfirmado(ordemServicoId);
        } else {
            eventosGateway.pagamentoRecusado(ordemServicoId);
        }
        return atual;
    }
}
