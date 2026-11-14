package com.fiap.cobranca.infra.gateway;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.domain.ResultadoCobranca;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * O adapter com o Resilience4j de verdade (aspectos do Spring), contra um servidor HTTP simulado: token, cache e
 * renovação do token, retry com fallback e circuit breaker.
 */
@SpringBootTest(classes = GatewayPagamentoHttpTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "resilience4j.retry.instances.gatewayPagamento.max-attempts=3",
        "resilience4j.retry.instances.gatewayPagamento.wait-duration=1ms",
        "resilience4j.retry.instances.gatewayPagamento.retry-exceptions=org.springframework.web.client.HttpServerErrorException,org.springframework.web.client.ResourceAccessException",
        "resilience4j.circuitbreaker.instances.gatewayPagamento.sliding-window-size=4",
        "resilience4j.circuitbreaker.instances.gatewayPagamento.minimum-number-of-calls=4",
        "resilience4j.circuitbreaker.instances.gatewayPagamento.failure-rate-threshold=75",
        "resilience4j.circuitbreaker.instances.gatewayPagamento.wait-duration-in-open-state=1h",
        "resilience4j.circuitbreaker.instances.gatewayPagamento.record-exceptions=org.springframework.web.client.HttpServerErrorException,org.springframework.web.client.ResourceAccessException"
})
class GatewayPagamentoHttpTest {

    private static final String URL = "http://meio-de-pagamento";
    private static final Pagamento PAGAMENTO = Pagamento.novo(7L, 2L, new BigDecimal("150.00"), Instant.EPOCH);
    private static final String TOKEN_JSON = "{\"access_token\":\"tk-%s\",\"expires_in\":300}";

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({AopAutoConfiguration.class, RetryAutoConfiguration.class, CircuitBreakerAutoConfiguration.class})
    @Import(GatewayPagamentoHttp.class)
    static class Config {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder().baseUrl(URL);
        }

        @Bean
        MockRestServiceServer servidor(RestClient.Builder builder) {
            return MockRestServiceServer.bindTo(builder).build();
        }

        @Bean
        RestClient gatewayPagamentoRestClient(RestClient.Builder builder, MockRestServiceServer servidor) {
            return builder.build();
        }

        @Bean
        GatewayPagamentoHttpProperties properties() {
            return new GatewayPagamentoHttpProperties(URL, "cobranca", "segredo", Duration.ofSeconds(1), Duration.ofSeconds(1));
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired private GatewayPagamentoHttp gateway;
    @Autowired private MockRestServiceServer servidor;
    @Autowired private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void setup() {
        servidor.reset();
        circuitBreakers.circuitBreaker(GatewayPagamentoHttp.RESILIENCIA).reset();
        // O token fica em cache no bean (alvo do proxy): cada teste começa sem token.
        GatewayPagamentoHttp alvo = AopTestUtils.getTargetObject(gateway);
        ReflectionTestUtils.setField(alvo, "token", null);
    }

    private void esperarToken(String sufixo) {
        servidor.expect(once(), requestTo(URL + "/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.client_id").value("cobranca"))
                .andExpect(jsonPath("$.client_secret").value("segredo"))
                .andExpect(jsonPath("$.grant_type").value("client_credentials"))
                .andRespond(withSuccess(TOKEN_JSON.formatted(sufixo), MediaType.APPLICATION_JSON));
    }

    private ResponseActions esperarPagamento(int vezes, String token) {
        return servidor.expect(times(vezes), requestTo(URL + "/pagamentos"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.ordemId").value(7))
                .andExpect(jsonPath("$.valor").value(150.00));
    }

    private static String pagamento(String status) {
        return "{\"id\":\"p-1\",\"status\":\"" + status + "\"}";
    }

    @Test
    void shouldObterTokenEDevolverAprovado() {
        esperarToken("1");
        esperarPagamento(1, "tk-1").andRespond(withSuccess(pagamento("APROVADO"), MediaType.APPLICATION_JSON));

        assertEquals(ResultadoCobranca.APROVADO, gateway.iniciarCobranca(PAGAMENTO));
        servidor.verify();
    }

    @Test
    void shouldReaproveitarOTokenEmCache() {
        esperarToken("1");
        esperarPagamento(2, "tk-1").andRespond(withSuccess(pagamento("RECUSADO"), MediaType.APPLICATION_JSON));

        assertEquals(ResultadoCobranca.RECUSADO, gateway.iniciarCobranca(PAGAMENTO));
        assertEquals(ResultadoCobranca.RECUSADO, gateway.iniciarCobranca(PAGAMENTO));
        servidor.verify();
    }

    @Test
    void shouldRenovarOTokenQuandoOMeioResponde401() {
        esperarToken("1");
        esperarPagamento(1, "tk-1").andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        esperarToken("2");
        esperarPagamento(1, "tk-2").andRespond(withSuccess(pagamento("APROVADO"), MediaType.APPLICATION_JSON));

        assertEquals(ResultadoCobranca.APROVADO, gateway.iniciarCobranca(PAGAMENTO));
        servidor.verify();
    }

    @Test
    void shouldRepetirApos503EAprovarNaTerceiraTentativa() {
        esperarToken("1");
        esperarPagamento(2, "tk-1").andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        esperarPagamento(1, "tk-1").andRespond(withSuccess(pagamento("APROVADO"), MediaType.APPLICATION_JSON));

        assertEquals(ResultadoCobranca.APROVADO, gateway.iniciarCobranca(PAGAMENTO));
        servidor.verify();
    }

    @Test
    void shouldDeixarPendenteQuandoAsTentativasSeEsgotam() {
        esperarToken("1");
        esperarPagamento(3, "tk-1").andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertEquals(ResultadoCobranca.PENDENTE, gateway.iniciarCobranca(PAGAMENTO));
        servidor.verify();
    }

    @Test
    void shouldDeixarPendenteSemRepetirQuandoASecretEstaErrada() {
        servidor.expect(times(2), requestTo(URL + "/oauth/token")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertEquals(ResultadoCobranca.PENDENTE, gateway.iniciarCobranca(PAGAMENTO));
        servidor.verify();
    }

    @Test
    void shouldDeixarPendenteQuandoOStatusEDesconhecido() {
        esperarToken("1");
        esperarPagamento(1, "tk-1").andRespond(withSuccess(pagamento("EM_ANALISE"), MediaType.APPLICATION_JSON));

        assertEquals(ResultadoCobranca.PENDENTE, gateway.iniciarCobranca(PAGAMENTO));
        servidor.verify();
    }

    @Test
    void shouldAbrirOCircuitoEPararDeChamarOMeio() {
        esperarToken("1");
        // 1o pagamento: 3 tentativas com 503. 2o: a 4a falha enche a janela (4/4 = 100%) e o circuito abre.
        esperarPagamento(4, "tk-1").andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertEquals(ResultadoCobranca.PENDENTE, gateway.iniciarCobranca(PAGAMENTO));
        assertEquals(ResultadoCobranca.PENDENTE, gateway.iniciarCobranca(PAGAMENTO));
        // Circuito aberto: nenhuma chamada sai (o servidor não espera mais nada) e o fallback responde na hora.
        assertEquals(ResultadoCobranca.PENDENTE, gateway.iniciarCobranca(PAGAMENTO));

        servidor.verify();
        assertEquals(CircuitBreaker.State.OPEN,
                circuitBreakers.circuitBreaker(GatewayPagamentoHttp.RESILIENCIA).getState());
    }
}
