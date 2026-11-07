package com.fiap.cobranca.core.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PagamentoUnitTest {

    private static final Instant AGORA = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void shouldCriarPagamentoPendente() {
        Pagamento p = Pagamento.novo(1L, 2L, new BigDecimal("150.50"), AGORA);

        assertEquals(StatusPagamento.PENDENTE, p.status());
        assertEquals(1L, p.ordemServicoId());
        assertEquals(2L, p.clienteId());
        assertEquals(new BigDecimal("150.50"), p.valor());
        assertEquals(AGORA, p.criadoEm());
        assertEquals(AGORA, p.atualizadoEm());
    }

    @Test
    void shouldRejeitarDadosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> Pagamento.novo(null, 2L, BigDecimal.ONE, AGORA));
        assertThrows(IllegalArgumentException.class, () -> Pagamento.novo(1L, null, BigDecimal.ONE, AGORA));
        assertThrows(IllegalArgumentException.class, () -> Pagamento.novo(1L, 2L, null, AGORA));
        assertThrows(IllegalArgumentException.class, () -> Pagamento.novo(1L, 2L, BigDecimal.ZERO, AGORA));
        assertThrows(IllegalArgumentException.class, () -> Pagamento.novo(1L, 2L, new BigDecimal("-1"), AGORA));
    }
}
