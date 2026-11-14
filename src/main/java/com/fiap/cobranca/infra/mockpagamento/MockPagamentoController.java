package com.fiap.cobranca.infra.mockpagamento;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock de um meio de pagamento externo (no formato do Mercado Pago: secret → token → pagamento), só para demonstrar o
 * adapter HTTP e a resiliência. É o meio de pagamento do fluxo hoje: o {@code GatewayPagamentoHttp} chama este
 * próprio serviço.
 * <p>
 * Comportamento do pagamento, por um contador de chamadas (n % 4): 1ª e 2ª → 503, 3ª → APROVADO, 4ª → RECUSADO, e
 * recomeça. Com o retry de 3 tentativas do adapter, o 1º pagamento é aprovado na 3ª tentativa e o 2º é recusado.
 * Valor terminado em .99 é sempre recusado, sem passar pelo contador.
 */
@RestController
@RequestMapping("/mock-pagamentos")
@Hidden
@Slf4j
public class MockPagamentoController {

    static final long VALIDADE_DO_TOKEN_SEGUNDOS = 300;
    private static final String BEARER = "Bearer ";

    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private final AtomicInteger chamadas = new AtomicInteger();
    private final Map<String, Instant> tokens = new ConcurrentHashMap<>();

    public MockPagamentoController(@Value("${cobranca.pagamento.mock.client-id}") String clientId,
                                   @Value("${cobranca.pagamento.mock.client-secret}") String clientSecret,
                                   Clock clock) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    public record TokenRequest(@JsonProperty("client_id") String clientId, @JsonProperty("client_secret") String clientSecret,
                               @JsonProperty("grant_type") String grantType) {
    }

    public record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {
    }

    public record PagamentoRequest(Long ordemId, BigDecimal valor) {
    }

    public record PagamentoResponse(String id, String status) {
    }

    @PostMapping("/oauth/token")
    public ResponseEntity<TokenResponse> token(@RequestBody TokenRequest request) {
        if (!clientId.equals(request.clientId()) || !clientSecret.equals(request.clientSecret())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = UUID.randomUUID().toString();
        tokens.put(token, clock.instant().plusSeconds(VALIDADE_DO_TOKEN_SEGUNDOS));
        return ResponseEntity.ok(new TokenResponse(token, VALIDADE_DO_TOKEN_SEGUNDOS));
    }

    @PostMapping("/pagamentos")
    public ResponseEntity<PagamentoResponse> pagar(@RequestHeader(name = "Authorization", required = false) String authorization,
                                                   @RequestBody PagamentoRequest request) {
        if (!tokenValido(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request.valor() != null && request.valor().remainder(BigDecimal.ONE).compareTo(new BigDecimal("0.99")) == 0) {
            log.info("Mock de pagamento: ordem {} recusada (valor terminado em .99)", request.ordemId());
            return ResponseEntity.ok(new PagamentoResponse(UUID.randomUUID().toString(), "RECUSADO"));
        }
        int chamada = chamadas.incrementAndGet();
        log.info("Mock de pagamento: chamada {} para a ordem {}", chamada, request.ordemId());
        return switch (chamada % 4) {
            case 1, 2 -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            case 3 -> ResponseEntity.ok(new PagamentoResponse(UUID.randomUUID().toString(), "APROVADO"));
            default -> ResponseEntity.ok(new PagamentoResponse(UUID.randomUUID().toString(), "RECUSADO"));
        };
    }

    /** Zera o contador e os tokens emitidos (testes e demonstração). */
    public void reiniciar() {
        chamadas.set(0);
        tokens.clear();
    }

    private boolean tokenValido(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return false;
        }
        Instant expiraEm = tokens.get(authorization.substring(BEARER.length()));
        return expiraEm != null && clock.instant().isBefore(expiraEm);
    }
}
