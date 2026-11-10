package com.fiap.cobranca.infra.gateway;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.domain.StatusPagamento;
import com.fiap.cobranca.resources.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** O gateway contra um DynamoDB Local de verdade: escritas condicionais é o que garante idempotência e corrida. */
class PagamentoDynamoGatewayIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(1_000);
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-01T10:05:00Z");

    @Autowired private PagamentoDynamoGateway gateway;

    @Test
    void shouldGravarELerOPagamento() {
        Long os = IDS.incrementAndGet();

        assertTrue(gateway.criarSeNaoExistir(Pagamento.novo(os, 9L, new BigDecimal("1234.56"), T0)));

        Pagamento lido = gateway.buscar(os).orElseThrow();
        assertEquals(os, lido.ordemServicoId());
        assertEquals(9L, lido.clienteId());
        assertEquals(0, new BigDecimal("1234.56").compareTo(lido.valor()));
        assertEquals(StatusPagamento.PENDENTE, lido.status());
        assertEquals(T0, lido.criadoEm());
        assertEquals(T0, lido.atualizadoEm());
    }

    @Test
    void shouldNaoSobrescreverQuandoJaExiste() {
        Long os = IDS.incrementAndGet();
        assertTrue(gateway.criarSeNaoExistir(Pagamento.novo(os, 9L, BigDecimal.TEN, T0)));

        assertFalse(gateway.criarSeNaoExistir(Pagamento.novo(os, 7L, BigDecimal.ONE, T1)));

        Pagamento lido = gateway.buscar(os).orElseThrow();
        assertEquals(9L, lido.clienteId());
        assertEquals(0, BigDecimal.TEN.compareTo(lido.valor()));
    }

    @Test
    void shouldRetornarVazioQuandoNaoExiste() {
        assertEquals(Optional.empty(), gateway.buscar(IDS.incrementAndGet()));
    }

    @Test
    void shouldTransicionarSoQuandoOStatusAtualForOEsperado() {
        Long os = IDS.incrementAndGet();
        gateway.criarSeNaoExistir(Pagamento.novo(os, 9L, BigDecimal.TEN, T0));

        assertTrue(gateway.transicionar(os, StatusPagamento.PENDENTE, StatusPagamento.CONFIRMADO, T1));
        assertFalse(gateway.transicionar(os, StatusPagamento.PENDENTE, StatusPagamento.RECUSADO, T1));

        Pagamento lido = gateway.buscar(os).orElseThrow();
        assertEquals(StatusPagamento.CONFIRMADO, lido.status());
        assertEquals(T1, lido.atualizadoEm());
        assertEquals(T0, lido.criadoEm());
    }

    @Test
    void shouldNaoTransicionarPagamentoInexistente() {
        assertFalse(gateway.transicionar(IDS.incrementAndGet(), StatusPagamento.PENDENTE, StatusPagamento.CONFIRMADO, T1));
        assertTrue(gateway.buscar(IDS.get()).isEmpty(), "a escrita condicional nao pode criar o item");
    }
}
