package com.fiap.cobranca.infra.mensageria;

import com.fiap.cobranca.core.usecase.SolicitarPagamentoUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitarPagamentoListenerUnitTest {

    @Mock private SolicitarPagamentoUseCase useCase;
    @InjectMocks private SolicitarPagamentoListener listener;

    @Test
    void shouldRegistrarOPagamento() {
        when(useCase.executar(1L, 2L, BigDecimal.TEN)).thenReturn(true);

        assertDoesNotThrow(() -> listener.solicitar(new SolicitarPagamentoComando(1L, 2L, BigDecimal.TEN)));

        verify(useCase).executar(1L, 2L, BigDecimal.TEN);
    }

    @Test
    void shouldAceitarComandoRepetido() {
        when(useCase.executar(1L, 2L, BigDecimal.TEN)).thenReturn(false);

        assertDoesNotThrow(() -> listener.solicitar(new SolicitarPagamentoComando(1L, 2L, BigDecimal.TEN)));
    }

    @Test
    void shouldMandarParaADlqQuandoOComandoEInvalido() {
        when(useCase.executar(1L, null, BigDecimal.TEN)).thenThrow(new IllegalArgumentException("sem cliente"));

        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> listener.solicitar(new SolicitarPagamentoComando(1L, null, BigDecimal.TEN)));
    }

    @Test
    void shouldMandarParaADlqQuandoOComandoEVazio() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.solicitar(null));
    }

    @Test
    void shouldPropagarFalhaDeInfraestruturaParaORetry() {
        when(useCase.executar(1L, 2L, BigDecimal.TEN)).thenThrow(new IllegalStateException("dynamo fora"));

        assertThrows(IllegalStateException.class, () -> listener.solicitar(new SolicitarPagamentoComando(1L, 2L, BigDecimal.TEN)));
    }
}
