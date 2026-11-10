package com.fiap.cobranca.shared.exception;

import com.fiap.cobranca.core.exception.PagamentoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldResponderComOStatusDaExcecaoDeNegocio() {
        var r = handler.baseExceptionHandler(new PagamentoNaoEncontradoException(1L));

        assertEquals(404, r.getStatusCode().value());
        assertEquals(404, r.getBody().status());
    }

    @Test
    void shouldResponder500ParaFalhaDeInfraestrutura() {
        BaseException e = new BaseException(503, "indisponivel") { };

        assertEquals(503, handler.baseExceptionHandler(e).getStatusCode().value());
    }

    @Test
    void shouldResponder400ParaArgumentoInvalido() {
        assertEquals(400, handler.illegalArgumentExceptionHandler(new IllegalArgumentException("x")).getStatusCode().value());
    }

    @Test
    void shouldResponder400ParaCorpoIlegivel() {
        assertEquals(400, handler.httpMessageNotReadableExceptionHandler(mock(HttpMessageNotReadableException.class)).getStatusCode().value());
    }

    @Test
    void shouldResponder500ParaErroInesperado() {
        assertEquals(500, handler.exceptionHandler(new RuntimeException("boom")).getStatusCode().value());
    }
}
