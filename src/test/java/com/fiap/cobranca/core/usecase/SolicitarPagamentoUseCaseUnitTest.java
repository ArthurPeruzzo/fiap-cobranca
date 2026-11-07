package com.fiap.cobranca.core.usecase;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.domain.StatusPagamento;
import com.fiap.cobranca.core.gateway.GatewayDePagamento;
import com.fiap.cobranca.core.gateway.PagamentoGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitarPagamentoUseCaseUnitTest {

    private static final Instant AGORA = Instant.parse("2026-01-01T10:00:00Z");

    @Mock private PagamentoGateway pagamentoGateway;
    @Mock private GatewayDePagamento gatewayDePagamento;
    private SolicitarPagamentoUseCase useCase;

    @BeforeEach
    void setup() {
        useCase = new SolicitarPagamentoUseCase(pagamentoGateway, gatewayDePagamento, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void shouldCriarPagamentoPendenteEIniciarACobranca() {
        when(pagamentoGateway.criarSeNaoExistir(any())).thenReturn(true);

        assertTrue(useCase.executar(1L, 2L, new BigDecimal("99.90")));

        ArgumentCaptor<Pagamento> captor = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentoGateway).criarSeNaoExistir(captor.capture());
        assertEquals(StatusPagamento.PENDENTE, captor.getValue().status());
        assertEquals(AGORA, captor.getValue().criadoEm());
        verify(gatewayDePagamento).iniciarCobranca(captor.getValue());
    }

    @Test
    void shouldIgnorarComandoRepetidoSemIniciarOutraCobranca() {
        when(pagamentoGateway.criarSeNaoExistir(any())).thenReturn(false);

        assertFalse(useCase.executar(1L, 2L, BigDecimal.TEN));

        verify(gatewayDePagamento, never()).iniciarCobranca(any());
    }

    @Test
    void shouldRejeitarComandoInvalidoSemTocarNoBanco() {
        assertThrows(IllegalArgumentException.class, () -> useCase.executar(1L, 2L, BigDecimal.ZERO));

        verifyNoInteractions(pagamentoGateway, gatewayDePagamento);
    }
}
