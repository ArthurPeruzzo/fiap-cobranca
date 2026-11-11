package com.fiap.cobranca.infra.mensageria;

import com.fiap.cobranca.core.gateway.GatewayDePagamento;
import com.fiap.cobranca.core.gateway.PagamentoEventosGateway;
import com.fiap.cobranca.core.gateway.PagamentoGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitarPagamentoListenerUnitTest {

    @Mock private PagamentoGateway pagamentoGateway;
    @Mock private PagamentoEventosGateway eventosGateway;
    @Mock private GatewayDePagamento gatewayDePagamento;
    private SolicitarPagamentoListener listener;

    @BeforeEach
    void setup() {
        listener = new SolicitarPagamentoListener(pagamentoGateway, eventosGateway, gatewayDePagamento,
                Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void shouldRegistrarOPagamentoEIniciarACobranca() {
        when(pagamentoGateway.criarSeNaoExistir(any())).thenReturn(true);

        assertDoesNotThrow(() -> listener.solicitar(new SolicitarPagamentoComando(1L, 2L, BigDecimal.TEN)));

        verify(gatewayDePagamento).iniciarCobranca(any());
    }

    @Test
    void shouldAceitarComandoRepetidoSemIniciarOutraCobranca() {
        when(pagamentoGateway.criarSeNaoExistir(any())).thenReturn(false);

        assertDoesNotThrow(() -> listener.solicitar(new SolicitarPagamentoComando(1L, 2L, BigDecimal.TEN)));

        verify(gatewayDePagamento, never()).iniciarCobranca(any());
    }

    @Test
    void shouldMandarParaADlqQuandoOComandoEInvalido() {
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> listener.solicitar(new SolicitarPagamentoComando(1L, null, BigDecimal.TEN)));

        verifyNoInteractions(pagamentoGateway, gatewayDePagamento);
    }

    @Test
    void shouldMandarParaADlqQuandoOComandoEVazio() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.solicitar(null));
    }

    @Test
    void shouldPropagarFalhaDeInfraestruturaParaORetry() {
        when(pagamentoGateway.criarSeNaoExistir(any())).thenThrow(new IllegalStateException("dynamo fora"));

        assertThrows(IllegalStateException.class, () -> listener.solicitar(new SolicitarPagamentoComando(1L, 2L, BigDecimal.TEN)));
    }
}
