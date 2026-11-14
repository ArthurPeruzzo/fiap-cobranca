package com.fiap.cobranca.infra.mockpagamento;

import com.fiap.cobranca.infra.mockpagamento.MockPagamentoController.PagamentoRequest;
import com.fiap.cobranca.infra.mockpagamento.MockPagamentoController.PagamentoResponse;
import com.fiap.cobranca.infra.mockpagamento.MockPagamentoController.TokenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MockPagamentoControllerUnitTest {

    private static final Instant AGORA = Instant.parse("2026-01-01T10:00:00Z");

    /** Relógio que o teste avança, para expirar o token. */
    private static final class RelogioAjustavel extends Clock {
        private Instant agora = AGORA;

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }

    private RelogioAjustavel relogio;
    private MockPagamentoController mock;

    @BeforeEach
    void setup() {
        relogio = new RelogioAjustavel();
        mock = new MockPagamentoController("cobranca", "segredo", relogio);
    }

    private String bearer() {
        var resposta = mock.token(new TokenRequest("cobranca", "segredo", "client_credentials"));
        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals(MockPagamentoController.VALIDADE_DO_TOKEN_SEGUNDOS, resposta.getBody().expiresIn());
        return "Bearer " + resposta.getBody().accessToken();
    }

    private ResponseEntity<PagamentoResponse> pagar(String bearer, String valor) {
        return mock.pagar(bearer, new PagamentoRequest(1L, new BigDecimal(valor)));
    }

    @Test
    void shouldRecusarTokenComSecretErrada() {
        assertEquals(HttpStatus.UNAUTHORIZED, mock.token(new TokenRequest("cobranca", "errada", null)).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, mock.token(new TokenRequest("outro", "segredo", null)).getStatusCode());
    }

    @Test
    void shouldExigirBearerValido() {
        assertEquals(HttpStatus.UNAUTHORIZED, pagar(null, "10").getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, pagar("Basic abc", "10").getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, pagar("Bearer inventado", "10").getStatusCode());
    }

    @Test
    void shouldRecusarTokenExpirado() {
        String bearer = bearer();
        relogio.agora = AGORA.plusSeconds(MockPagamentoController.VALIDADE_DO_TOKEN_SEGUNDOS);

        assertEquals(HttpStatus.UNAUTHORIZED, pagar(bearer, "10").getStatusCode());
    }

    @Test
    void shouldSeguirOCicloDoContador() {
        String bearer = bearer();
        List<String> respostas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            var resposta = pagar(bearer, "10.00");
            respostas.add(resposta.getStatusCode() == HttpStatus.OK ? resposta.getBody().status() : "503");
        }

        assertEquals(List.of("503", "503", "APROVADO", "RECUSADO", "503", "503", "APROVADO", "RECUSADO"), respostas);
    }

    @Test
    void shouldSempreRecusarValorTerminadoEm99SemAvancarOContador() {
        String bearer = bearer();

        assertEquals("RECUSADO", pagar(bearer, "100.99").getBody().status());
        assertEquals("RECUSADO", pagar(bearer, "0.99").getBody().status());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, pagar(bearer, "100.98").getStatusCode(), "contador comeca do 1");
    }

    @Test
    void shouldReiniciarContadorETokens() {
        String bearer = bearer();
        pagar(bearer, "10");

        mock.reiniciar();

        assertEquals(HttpStatus.UNAUTHORIZED, pagar(bearer, "10").getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, pagar(bearer(), "10").getStatusCode(), "volta para a 1a chamada");
    }
}
