package com.fiap.cobranca.core.usecase;

import com.fiap.cobranca.core.dto.SolicitarPagamentoDto;
import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.domain.ResultadoCobranca;
import com.fiap.cobranca.core.domain.StatusPagamento;
import com.fiap.cobranca.core.gateway.GatewayDePagamento;
import com.fiap.cobranca.core.gateway.PagamentoEventosGateway;
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
import java.util.Optional;

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
    @Mock private PagamentoEventosGateway eventosGateway;
    @Mock private GatewayDePagamento gatewayDePagamento;
    private SolicitarPagamentoUseCase useCase;

    @BeforeEach
    void setup() {
        useCase = new SolicitarPagamentoUseCase(pagamentoGateway, eventosGateway, gatewayDePagamento,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static Pagamento comStatus(StatusPagamento status) {
        return new Pagamento(1L, 2L, BigDecimal.TEN, status, AGORA, AGORA);
    }

    @Test
    void shouldCriarPagamentoPendenteEIniciarACobranca() {
        when(pagamentoGateway.criarSeNaoExistir(any())).thenReturn(true);
        when(gatewayDePagamento.iniciarCobranca(any())).thenReturn(ResultadoCobranca.PENDENTE);

        assertTrue(useCase.solicitar(new SolicitarPagamentoDto(1L, 2L, new BigDecimal("99.90"))));

        ArgumentCaptor<Pagamento> captor = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentoGateway).criarSeNaoExistir(captor.capture());
        assertEquals(StatusPagamento.PENDENTE, captor.getValue().status());
        assertEquals(AGORA, captor.getValue().criadoEm());
        verify(gatewayDePagamento).iniciarCobranca(captor.getValue());
        verify(pagamentoGateway, never()).transicionar(any(), any(), any(), any());
        verifyNoInteractions(eventosGateway);
    }

    @Test
    void shouldConfirmarEAvisarAOsQuandoOMeioAprova() {
        when(pagamentoGateway.criarSeNaoExistir(any())).thenReturn(true);
        when(gatewayDePagamento.iniciarCobranca(any())).thenReturn(ResultadoCobranca.APROVADO);
        when(pagamentoGateway.buscar(1L)).thenReturn(Optional.of(comStatus(StatusPagamento.PENDENTE)),
                Optional.of(comStatus(StatusPagamento.CONFIRMADO)));

        assertTrue(useCase.solicitar(new SolicitarPagamentoDto(1L, 2L, BigDecimal.TEN)));

        verify(pagamentoGateway).transicionar(1L, StatusPagamento.PENDENTE, StatusPagamento.CONFIRMADO, AGORA);
        verify(eventosGateway).pagamentoConfirmado(1L);
    }

    @Test
    void shouldRecusarEAvisarAOsQuandoOMeioRecusa() {
        when(pagamentoGateway.criarSeNaoExistir(any())).thenReturn(true);
        when(gatewayDePagamento.iniciarCobranca(any())).thenReturn(ResultadoCobranca.RECUSADO);
        when(pagamentoGateway.buscar(1L)).thenReturn(Optional.of(comStatus(StatusPagamento.PENDENTE)),
                Optional.of(comStatus(StatusPagamento.RECUSADO)));

        assertTrue(useCase.solicitar(new SolicitarPagamentoDto(1L, 2L, BigDecimal.TEN)));

        verify(pagamentoGateway).transicionar(1L, StatusPagamento.PENDENTE, StatusPagamento.RECUSADO, AGORA);
        verify(eventosGateway).pagamentoRecusado(1L);
    }

    @Test
    void shouldIgnorarComandoRepetidoSemIniciarOutraCobranca() {
        when(pagamentoGateway.criarSeNaoExistir(any())).thenReturn(false);

        assertFalse(useCase.solicitar(new SolicitarPagamentoDto(1L, 2L, BigDecimal.TEN)));

        verify(gatewayDePagamento, never()).iniciarCobranca(any());
    }

    @Test
    void shouldRejeitarComandoInvalidoSemTocarNoBanco() {
        assertThrows(IllegalArgumentException.class, () -> useCase.solicitar(new SolicitarPagamentoDto(1L, 2L, BigDecimal.ZERO)));

        verifyNoInteractions(pagamentoGateway, gatewayDePagamento);
    }
}
