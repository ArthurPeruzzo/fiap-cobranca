package com.fiap.cobranca.core.usecase;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.domain.StatusPagamento;
import com.fiap.cobranca.core.exception.PagamentoNaoEncontradoException;
import com.fiap.cobranca.core.gateway.PagamentoGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarPagamentoUseCaseUnitTest {

    @Mock private PagamentoGateway pagamentoGateway;
    @Mock private ConsultarPagamentoOutputPort outputPort;
    private ConsultarPagamentoUseCase useCase;

    @BeforeEach
    void setup() {
        useCase = new ConsultarPagamentoUseCase(pagamentoGateway, outputPort);
    }

    @Test
    void shouldRetornarOPagamento() {
        Pagamento p = new Pagamento(1L, 2L, BigDecimal.TEN, StatusPagamento.PENDENTE, Instant.EPOCH, Instant.EPOCH);
        when(pagamentoGateway.buscar(1L)).thenReturn(Optional.of(p));

        useCase.consultar(1L);

        verify(outputPort).apresentar(p);
    }

    @Test
    void shouldFalharQuandoNaoExiste() {
        when(pagamentoGateway.buscar(1L)).thenReturn(Optional.empty());

        PagamentoNaoEncontradoException e = assertThrows(PagamentoNaoEncontradoException.class, () -> useCase.consultar(1L));
        assertEquals(404, e.getStatusCode());
        verifyNoInteractions(outputPort);
    }
}
