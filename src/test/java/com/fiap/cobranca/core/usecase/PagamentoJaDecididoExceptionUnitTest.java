package com.fiap.cobranca.core.usecase;

import com.fiap.cobranca.core.domain.StatusPagamento;
import com.fiap.cobranca.core.exception.PagamentoJaDecididoException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagamentoJaDecididoExceptionUnitTest {

    @Test
    void shouldSerConflito() {
        PagamentoJaDecididoException e = new PagamentoJaDecididoException(3L, StatusPagamento.RECUSADO);

        assertEquals(409, e.getStatusCode());
        assertTrue(e.getMessage().contains("RECUSADO"));
    }
}
