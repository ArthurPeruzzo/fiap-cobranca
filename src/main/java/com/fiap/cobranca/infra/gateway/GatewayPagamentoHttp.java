package com.fiap.cobranca.infra.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.domain.ResultadoCobranca;
import com.fiap.cobranca.core.gateway.GatewayDePagamento;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * O meio de pagamento do fluxo: HTTP, secret → token → pagamento. Hoje aponta para o mock
 * ({@code infra/mockpagamento}); outro meio (Mercado Pago) entra como outra implementação da porta ou trocando
 * url e credenciais ({@code PAGAMENTO_*}).
 * <p>
 * Resiliência: o {@code @Retry} (externo) repete falhas de servidor/rede com backoff e o {@code @CircuitBreaker}
 * (interno, conta cada tentativa) para de chamar quando o meio está fora. Esgotadas as tentativas ou com o circuito
 * aberto, o fallback deixa o pagamento PENDENTE, e a equipe ainda pode decidi-lo pelos endpoints de
 * {@code /pagamentos}. O token é guardado até perto de expirar e renovado quando o meio responde 401.
 */
@Component
@Slf4j
public class GatewayPagamentoHttp implements GatewayDePagamento {

    static final String RESILIENCIA = "gatewayPagamento";
    private static final Duration MARGEM_DE_EXPIRACAO = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final GatewayPagamentoHttpProperties properties;
    private final Clock clock;
    private TokenEmCache token;

    public GatewayPagamentoHttp(RestClient gatewayPagamentoRestClient, GatewayPagamentoHttpProperties properties, Clock clock) {
        this.restClient = gatewayPagamentoRestClient;
        this.properties = properties;
        this.clock = clock;
    }

    record TokenRequest(@JsonProperty("client_id") String clientId, @JsonProperty("client_secret") String clientSecret,
                        @JsonProperty("grant_type") String grantType) {
    }

    record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {
    }

    record PagamentoRequest(Long ordemId, BigDecimal valor) {
    }

    record PagamentoResponse(String id, String status) {
    }

    private record TokenEmCache(String valor, Instant renovarEm) {
    }

    @Override
    @Retry(name = RESILIENCIA, fallbackMethod = "cobrancaPendente")
    @CircuitBreaker(name = RESILIENCIA)
    public ResultadoCobranca iniciarCobranca(Pagamento pagamento) {
        try {
            return cobrar(pagamento, token(false));
        } catch (HttpClientErrorException.Unauthorized e) {
            // Token expirado ou revogado no meio de pagamento: renova e tenta uma vez.
            return cobrar(pagamento, token(true));
        }
    }

    /** Fallback do retry: o meio de pagamento não decidiu, então o pagamento segue PENDENTE. */
    ResultadoCobranca cobrancaPendente(Pagamento pagamento, Throwable erro) {
        log.warn("Meio de pagamento indisponivel para a ordem de servico {}; pagamento segue PENDENTE: {}",
                pagamento.ordemServicoId(), erro.toString());
        return ResultadoCobranca.PENDENTE;
    }

    private ResultadoCobranca cobrar(Pagamento pagamento, String accessToken) {
        PagamentoResponse resposta = restClient.post().uri("/pagamentos")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(new PagamentoRequest(pagamento.ordemServicoId(), pagamento.valor()))
                .retrieve()
                .body(PagamentoResponse.class);
        String status = resposta == null ? null : resposta.status();
        log.info("Meio de pagamento respondeu {} para a ordem de servico {}", status, pagamento.ordemServicoId());
        if ("APROVADO".equals(status)) {
            return ResultadoCobranca.APROVADO;
        }
        if ("RECUSADO".equals(status)) {
            return ResultadoCobranca.RECUSADO;
        }
        return ResultadoCobranca.PENDENTE;
    }

    private synchronized String token(boolean renovar) {
        if (!renovar && token != null && clock.instant().isBefore(token.renovarEm())) {
            return token.valor();
        }
        TokenResponse resposta = restClient.post().uri("/oauth/token")
                .body(new TokenRequest(properties.clientId(), properties.clientSecret(), "client_credentials"))
                .retrieve()
                .body(TokenResponse.class);
        if (resposta == null || resposta.accessToken() == null) {
            throw new IllegalStateException("Meio de pagamento nao devolveu token");
        }
        token = new TokenEmCache(resposta.accessToken(),
                clock.instant().plusSeconds(resposta.expiresIn()).minus(MARGEM_DE_EXPIRACAO));
        return token.valor();
    }
}
