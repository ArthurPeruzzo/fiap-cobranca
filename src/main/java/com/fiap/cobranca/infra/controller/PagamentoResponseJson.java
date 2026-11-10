package com.fiap.cobranca.infra.controller;

import com.fiap.cobranca.core.domain.Pagamento;

import java.math.BigDecimal;
import java.time.Instant;

public record PagamentoResponseJson(Long ordemServicoId, Long clienteId, BigDecimal valor, String status,
                                    Instant criadoEm, Instant atualizadoEm) {

    static PagamentoResponseJson de(Pagamento p) {
        return new PagamentoResponseJson(p.ordemServicoId(), p.clienteId(), p.valor(), p.status().name(),
                p.criadoEm(), p.atualizadoEm());
    }
}
