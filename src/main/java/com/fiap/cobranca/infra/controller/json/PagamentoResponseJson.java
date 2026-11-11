package com.fiap.cobranca.infra.controller.json;

import java.math.BigDecimal;
import java.time.Instant;

public record PagamentoResponseJson(Long ordemServicoId, Long clienteId, BigDecimal valor, String status,
                                    Instant criadoEm, Instant atualizadoEm) {
}
