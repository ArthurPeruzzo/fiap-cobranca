package com.fiap.cobranca.infra.mensageria;

import java.math.BigDecimal;

/** Comando enviado pelo fiap-mecanica (outbox) quando o orçamento é aprovado. */
public record SolicitarPagamentoComando(Long ordemServicoId, Long clienteId, BigDecimal valor) {
}
