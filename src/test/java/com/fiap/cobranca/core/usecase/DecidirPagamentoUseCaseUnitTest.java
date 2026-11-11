package com.fiap.cobranca.core.usecase;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.domain.StatusPagamento;
import com.fiap.cobranca.core.exception.PagamentoJaDecididoException;
import com.fiap.cobranca.core.exception.PagamentoNaoEncontradoException;
import com.fiap.cobranca.core.gateway.PagamentoEventosGateway;
import com.fiap.cobranca.core.gateway.PagamentoGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class DecidirPagamentoUseCaseUnitTest {

    private static final Instant AGORA = Instant.parse("2026-01-01T10:00:00Z");
    private static final Long OS = 5L;

    @Mock private PagamentoGateway pagamentoGateway;
    @Mock private PagamentoEventosGateway eventosGateway;
    @Mock private DecidirPagamentoOutputPort outputPort;
    private DecidirPagamentoUseCase useCase;

    @BeforeEach
    void setup() {
        useCase = new DecidirPagamentoUseCase(pagamentoGateway, eventosGateway, Clock.fixed(AGORA, ZoneOffset.UTC), outputPort);
    }

    private static Pagamento com(StatusPagamento status) {
        return new Pagamento(OS, 2L, BigDecimal.TEN, status, AGORA, AGORA);
    }

    @Test
    void shouldConfirmarPagamentoPendenteEAvisarAOs() {
        when(pagamentoGateway.buscar(OS)).thenReturn(Optional.of(com(StatusPagamento.PENDENTE)), Optional.of(com(StatusPagamento.CONFIRMADO)));
        when(pagamentoGateway.transicionar(OS, StatusPagamento.PENDENTE, StatusPagamento.CONFIRMADO, AGORA)).thenReturn(true);

        useCase.confirmar(OS);

        verify(outputPort).apresentar(argThat(p -> p.status() == StatusPagamento.CONFIRMADO));
        verify(eventosGateway).pagamentoConfirmado(OS);
        verify(eventosGateway, never()).pagamentoRecusado(any());
    }

    @Test
    void shouldRecusarPagamentoPendenteEAvisarAOs() {
        when(pagamentoGateway.buscar(OS)).thenReturn(Optional.of(com(StatusPagamento.PENDENTE)), Optional.of(com(StatusPagamento.RECUSADO)));
        when(pagamentoGateway.transicionar(OS, StatusPagamento.PENDENTE, StatusPagamento.RECUSADO, AGORA)).thenReturn(true);

        useCase.recusar(OS);

        verify(outputPort).apresentar(argThat(p -> p.status() == StatusPagamento.RECUSADO));
        verify(eventosGateway).pagamentoRecusado(OS);
        verify(eventosGateway, never()).pagamentoConfirmado(any());
    }

    @Test
    void shouldReenviarARespostaQuandoADecisaoRepetidaJaEstaGravada() {
        when(pagamentoGateway.buscar(OS)).thenReturn(Optional.of(com(StatusPagamento.CONFIRMADO)));

        useCase.confirmar(OS);

        verify(pagamentoGateway, never()).transicionar(any(), any(), any(), any());
        verify(eventosGateway).pagamentoConfirmado(OS);
        verify(outputPort).apresentar(argThat(p -> p.status() == StatusPagamento.CONFIRMADO));
    }

    @Test
    void shouldRejeitarDecisaoContrariaAJaGravada() {
        when(pagamentoGateway.buscar(OS)).thenReturn(Optional.of(com(StatusPagamento.CONFIRMADO)));

        assertThrows(PagamentoJaDecididoException.class, () -> useCase.recusar(OS));

        verifyNoInteractions(eventosGateway, outputPort);
    }

    @Test
    void shouldRejeitarQuandoOutraDecisaoVenceuACorrida() {
        when(pagamentoGateway.buscar(OS)).thenReturn(Optional.of(com(StatusPagamento.PENDENTE)), Optional.of(com(StatusPagamento.RECUSADO)));
        when(pagamentoGateway.transicionar(OS, StatusPagamento.PENDENTE, StatusPagamento.CONFIRMADO, AGORA)).thenReturn(false);

        assertThrows(PagamentoJaDecididoException.class, () -> useCase.confirmar(OS));

        verifyNoInteractions(eventosGateway, outputPort);
    }

    @Test
    void shouldFalharQuandoNaoHaPagamentoParaAOs() {
        when(pagamentoGateway.buscar(OS)).thenReturn(Optional.empty());

        assertThrows(PagamentoNaoEncontradoException.class, () -> useCase.confirmar(OS));
    }

    @Test
    void shouldFalharQuandoOPagamentoSomeEntreALeituraEATransicao() {
        when(pagamentoGateway.buscar(OS)).thenReturn(Optional.of(com(StatusPagamento.PENDENTE)), Optional.empty());

        assertThrows(PagamentoNaoEncontradoException.class, () -> useCase.confirmar(OS));
    }

    @Test
    void shouldPropagarFalhaAoResponderParaPermitirNovaTentativa() {
        when(pagamentoGateway.buscar(OS)).thenReturn(Optional.of(com(StatusPagamento.CONFIRMADO)));
        org.mockito.Mockito.doThrow(new IllegalStateException("broker fora")).when(eventosGateway).pagamentoConfirmado(OS);

        assertThrows(IllegalStateException.class, () -> useCase.confirmar(OS));
    }
}
