package com.fiap.cobranca.core.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Pagamento de uma ordem de serviço. Há no máximo um por OS (a chave é o id da OS). */
public record Pagamento(Long ordemServicoId, Long clienteId, BigDecimal valor, StatusPagamento status,
                        Instant criadoEm, Instant atualizadoEm) {

    public static Pagamento novo(Long ordemServicoId, Long clienteId, BigDecimal valor, Instant agora) {
        if (ordemServicoId == null || clienteId == null || valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Pagamento exige ordemServicoId, clienteId e valor positivo");
        }
        return new Pagamento(ordemServicoId, clienteId, valor, StatusPagamento.PENDENTE, agora, agora);
    }
}
