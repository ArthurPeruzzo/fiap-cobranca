package com.fiap.cobranca.core.dto;

import java.math.BigDecimal;

public record SolicitarPagamentoDto(Long ordemServicoId, Long clienteId, BigDecimal valor) {
}
