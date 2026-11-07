package com.fiap.cobranca.core.gateway;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.domain.StatusPagamento;

import java.time.Instant;
import java.util.Optional;

public interface PagamentoGateway {

    /** Grava o pagamento só se ainda não existir um para a OS. @return false se já existia (mensagem repetida). */
    boolean criarSeNaoExistir(Pagamento pagamento);

    Optional<Pagamento> buscar(Long ordemServicoId);

    /** Muda o status só se o atual for {@code de} (escrita condicional). @return false se o status atual era outro. */
    boolean transicionar(Long ordemServicoId, StatusPagamento de, StatusPagamento para, Instant quando);
}
