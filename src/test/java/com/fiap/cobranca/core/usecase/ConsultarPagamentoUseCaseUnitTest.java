package com.fiap.cobranca.core.usecase;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.domain.StatusPagamento;
import com.fiap.cobranca.core.exception.PagamentoNaoEncontradoException;
import com.fiap.cobranca.core.gateway.PagamentoGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarPagamentoUseCaseUnitTest {

    @Mock private PagamentoGateway pagamentoGateway;
    @InjectMocks private ConsultarPagamentoUseCase useCase;

    @Test
    void shouldRetornarOPagamento() {
        Pagamento p = new Pagamento(1L, 2L, BigDecimal.TEN, StatusPagamento.PENDENTE, Instant.EPOCH, Instant.EPOCH);
        when(pagamentoGateway.buscar(1L)).thenReturn(Optional.of(p));

        assertSame(p, useCase.executar(1L));
    }

    @Test
    void shouldFalharQuandoNaoExiste() {
        when(pagamentoGateway.buscar(1L)).thenReturn(Optional.empty());

        PagamentoNaoEncontradoException e = assertThrows(PagamentoNaoEncontradoException.class, () -> useCase.executar(1L));
        assertEquals(404, e.getStatusCode());
    }
}
