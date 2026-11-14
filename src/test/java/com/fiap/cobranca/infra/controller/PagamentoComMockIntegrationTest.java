package com.fiap.cobranca.infra.controller;

import com.fiap.cobranca.infra.mockpagamento.MockPagamentoController;
import com.fiap.cobranca.resources.AbstractIntegrationTest;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * O fluxo principal: o comando da OS faz o {@code GatewayPagamentoHttp} chamar o mock (neste mesmo serviço, por HTTP de
 * verdade) e a decisão volta para a OS sem ninguém aprovar à mão. Mostra o retry vencendo os 503 do mock e a recusa
 * que dispara a compensação.
 */
class PagamentoComMockIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(5000);
    private static final String FILA_RESPOSTAS = "teste.respostas-da-os-mock";

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private MockPagamentoController mock;
    @Autowired private CircuitBreakerRegistry circuitBreakers;

    @Value("${cobranca.mensageria.exchange}") private String exchange;
    @Value("${cobranca.mensageria.routing-key.solicitar-pagamento}") private String routingKey;
    @Value("${ordemservico.mensageria.exchange}") private String exchangeRespostas;
    @Value("${cobranca.pagamento.mock.client-id}") private String clientId;
    @Value("${cobranca.pagamento.mock.client-secret}") private String clientSecret;

    @BeforeEach
    void setup() {
        RestAssured.port = PORTA;
        mock.reiniciar();
        circuitBreakers.circuitBreaker("gatewayPagamento").reset();
        Queue respostas = new Queue(FILA_RESPOSTAS, false);
        amqpAdmin.declareQueue(respostas);
        amqpAdmin.purgeQueue(FILA_RESPOSTAS, true);
        DirectExchange exchangeDaOs = new DirectExchange(exchangeRespostas, true, false);
        amqpAdmin.declareBinding(BindingBuilder.bind(respostas).to(exchangeDaOs).with("pagamento.confirmado"));
        amqpAdmin.declareBinding(BindingBuilder.bind(respostas).to(exchangeDaOs).with("pagamento.recusado"));
    }

    @AfterEach
    void limpar() {
        amqpAdmin.deleteQueue(FILA_RESPOSTAS);
    }

    private Long solicitar(String valor) {
        Long os = IDS.incrementAndGet();
        String json = "{\"ordemServicoId\":" + os + ",\"clienteId\":9,\"valor\":" + valor + "}";
        rabbitTemplate.send(exchange, routingKey, MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build());
        return os;
    }

    private void esperarStatus(Long os, String status) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                given().header("Authorization", "Bearer " + token("ROLE_ATENDENTE"))
                        .get("/pagamentos/" + os).then().statusCode(200).body("status", equalTo(status)));
    }

    private void esperarResposta(Long os, String routingKeyEsperada) {
        Message resposta = rabbitTemplate.receive(FILA_RESPOSTAS, Duration.ofSeconds(10).toMillis());
        assertNotNull(resposta, "a OS deveria receber a resposta do pagamento");
        assertEquals("{\"ordemServicoId\":" + os + "}", new String(resposta.getBody(), StandardCharsets.UTF_8));
        assertEquals(routingKeyEsperada, resposta.getMessageProperties().getReceivedRoutingKey());
    }

    @Test
    void shouldAprovarNaTerceiraTentativaERecusarOPagamentoSeguinte() {
        // Chamadas 1 e 2 do mock: 503 (o retry repete); chamada 3: aprovado.
        Long primeira = solicitar("250.00");
        esperarStatus(primeira, "CONFIRMADO");
        esperarResposta(primeira, "pagamento.confirmado");

        // Chamada 4: recusado, e a OS recebe pagamento.recusado (compensação da saga).
        Long segunda = solicitar("250.00");
        esperarStatus(segunda, "RECUSADO");
        esperarResposta(segunda, "pagamento.recusado");
    }

    @Test
    void shouldRecusarValorTerminadoEm99() {
        Long os = solicitar("120.99");

        esperarStatus(os, "RECUSADO");
        esperarResposta(os, "pagamento.recusado");
    }

    @Test
    void shouldExporOMockSemJwtComAutenticacaoPropria() {
        given().contentType(ContentType.JSON)
                .body("{\"client_id\":\"" + clientId + "\",\"client_secret\":\"errada\"}")
                .post("/mock-pagamentos/oauth/token").then().statusCode(401);
        given().contentType(ContentType.JSON)
                .body("{\"client_id\":\"" + clientId + "\",\"client_secret\":\"" + clientSecret + "\",\"grant_type\":\"client_credentials\"}")
                .post("/mock-pagamentos/oauth/token").then().statusCode(200)
                .body("access_token", notNullValue()).body("expires_in", equalTo(300));
        given().contentType(ContentType.JSON).body("{\"ordemId\":1,\"valor\":10}")
                .post("/mock-pagamentos/pagamentos").then().statusCode(401);
    }
}
