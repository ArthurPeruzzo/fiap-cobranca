package com.fiap.cobranca.infra.controller;

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
import static org.junit.jupiter.api.Assertions.*;

/**
 * O fluxo de ponta a ponta da cobrança contra RabbitMQ e DynamoDB reais quando o meio de pagamento está fora (circuito
 * forçado aberto): comando da OS → pagamento PENDENTE → decisão manual pelo endpoint protegido → resposta para a OS.
 * Cobre também idempotência, DLQ e a proteção dos endpoints. O fluxo com o meio no ar está em PagamentoComMockIntegrationTest.
 */
class PagamentoSagaIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(100);
    private static final String FILA_RESPOSTAS = "teste.respostas-da-os";

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private CircuitBreakerRegistry circuitBreakers;

    @Value("${cobranca.mensageria.exchange}") private String exchange;
    @Value("${cobranca.mensageria.fila.solicitar-pagamento}") private String fila;
    @Value("${cobranca.mensageria.routing-key.solicitar-pagamento}") private String routingKey;
    @Value("${ordemservico.mensageria.exchange}") private String exchangeRespostas;

    @BeforeEach
    void setup() {
        RestAssured.port = PORTA;
        // Meio de pagamento fora: o fallback deixa o pagamento PENDENTE para a decisão manual.
        circuitBreakers.circuitBreaker("gatewayPagamento").transitionToForcedOpenState();
        declararFilaDeRespostas();
        amqpAdmin.purgeQueue(fila + ".dlq", true);
    }

    @AfterEach
    void limpar() {
        circuitBreakers.circuitBreaker("gatewayPagamento").reset();
        amqpAdmin.deleteQueue(FILA_RESPOSTAS);
        amqpAdmin.purgeQueue(fila + ".dlq", true);
    }

    /** Faz o papel da fila ordemservico.respostas, que em produção é declarada pelo fiap-mecanica. */
    private void declararFilaDeRespostas() {
        Queue respostas = new Queue(FILA_RESPOSTAS, false);
        amqpAdmin.declareQueue(respostas);
        amqpAdmin.purgeQueue(FILA_RESPOSTAS, true);
        DirectExchange exchangeDaOs = new DirectExchange(exchangeRespostas, true, false);
        amqpAdmin.declareBinding(BindingBuilder.bind(respostas).to(exchangeDaOs).with("pagamento.confirmado"));
        amqpAdmin.declareBinding(BindingBuilder.bind(respostas).to(exchangeDaOs).with("pagamento.recusado"));
    }

    private void solicitar(String json) {
        Message mensagem = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbitTemplate.send(exchange, routingKey, mensagem);
    }

    /** Envia o comando como o fiap-mecanica envia e espera o pagamento aparecer PENDENTE. */
    private Long pagamentoPendente() {
        Long os = IDS.incrementAndGet();
        solicitar("{\"ordemServicoId\":" + os + ",\"clienteId\":9,\"valor\":250.75}");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                given().header("Authorization", "Bearer " + atendente())
                        .get("/pagamentos/" + os).then().statusCode(200).body("status", equalTo("PENDENTE")));
        return os;
    }

    private static String atendente() {
        return token("ROLE_ATENDENTE");
    }

    private Message receberResposta() {
        return rabbitTemplate.receive(FILA_RESPOSTAS, Duration.ofSeconds(10).toMillis());
    }

    @Test
    void shouldRegistrarPagamentoPendenteAoReceberOComando() {
        Long os = pagamentoPendente();

        given().header("Authorization", "Bearer " + atendente())
                .get("/pagamentos/" + os)
                .then().statusCode(200)
                .body("ordemServicoId", equalTo(os.intValue()))
                .body("clienteId", equalTo(9))
                .body("valor", equalTo(250.75f))
                .body("status", equalTo("PENDENTE"));
        assertNull(rabbitTemplate.receive(FILA_RESPOSTAS, 300), "pendente nao responde nada a OS");
    }

    @Test
    void shouldManterUmUnicoPagamentoQuandoOComandoChegaDuasVezes() {
        Long os = pagamentoPendente();
        given().header("Authorization", "Bearer " + atendente()).post("/pagamentos/" + os + "/aprovar").then().statusCode(200);

        solicitar("{\"ordemServicoId\":" + os + ",\"clienteId\":1,\"valor\":1}");

        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                given().header("Authorization", "Bearer " + atendente())
                        .get("/pagamentos/" + os)
                        .then().body("status", equalTo("CONFIRMADO")).body("clienteId", equalTo(9)));
    }

    @Test
    void shouldEnviarParaADlqQuandoOComandoEInvalido() {
        solicitar("{\"ordemServicoId\":" + IDS.incrementAndGet() + ",\"clienteId\":9,\"valor\":0}");

        assertNotNull(rabbitTemplate.receive(fila + ".dlq", Duration.ofSeconds(10).toMillis()),
                "comando invalido vai direto para a DLQ");
    }

    @Test
    void shouldAprovarEResponderPagamentoConfirmado() {
        Long os = pagamentoPendente();

        given().header("Authorization", "Bearer " + atendente())
                .post("/pagamentos/" + os + "/aprovar")
                .then().statusCode(200).body("status", equalTo("CONFIRMADO"));

        Message resposta = receberResposta();
        assertNotNull(resposta, "a OS deveria receber PagamentoConfirmado");
        assertEquals("{\"ordemServicoId\":" + os + "}", new String(resposta.getBody(), StandardCharsets.UTF_8));
        assertEquals("pagamento.confirmado", resposta.getMessageProperties().getReceivedRoutingKey());
        assertEquals("PAGAMENTO_CONFIRMADO", resposta.getMessageProperties().getType());
    }

    @Test
    void shouldRecusarEResponderPagamentoRecusado() {
        Long os = pagamentoPendente();

        given().header("Authorization", "Bearer " + token("ROLE_ADMINISTRADOR"))
                .post("/pagamentos/" + os + "/recusar")
                .then().statusCode(200).body("status", equalTo("RECUSADO"));

        Message resposta = receberResposta();
        assertNotNull(resposta);
        assertEquals("pagamento.recusado", resposta.getMessageProperties().getReceivedRoutingKey());
        assertEquals("PAGAMENTO_RECUSADO", resposta.getMessageProperties().getType());
    }

    @Test
    void shouldReenviarARespostaQuandoAMesmaDecisaoEhRepetida() {
        Long os = pagamentoPendente();
        String auth = "Bearer " + atendente();
        given().header("Authorization", auth).post("/pagamentos/" + os + "/aprovar").then().statusCode(200);
        assertNotNull(receberResposta());

        given().header("Authorization", auth).post("/pagamentos/" + os + "/aprovar").then().statusCode(200);

        assertNotNull(receberResposta(), "repetir a decisao reenvia a resposta (a primeira pode ter se perdido)");
    }

    @Test
    void shouldRejeitarDecisaoContrariaComConflito() {
        Long os = pagamentoPendente();
        String auth = "Bearer " + atendente();
        given().header("Authorization", auth).post("/pagamentos/" + os + "/aprovar").then().statusCode(200);
        assertNotNull(receberResposta());

        given().header("Authorization", auth).post("/pagamentos/" + os + "/recusar").then().statusCode(409);

        assertNull(rabbitTemplate.receive(FILA_RESPOSTAS, 300), "conflito nao envia resposta");
    }

    @Test
    void shouldResponder404QuandoNaoHaPagamento() {
        given().header("Authorization", "Bearer " + atendente())
                .post("/pagamentos/" + IDS.incrementAndGet() + "/aprovar").then().statusCode(404);
        given().header("Authorization", "Bearer " + atendente())
                .get("/pagamentos/" + IDS.incrementAndGet()).then().statusCode(404);
    }

    @Test
    void shouldPermitirNovaTentativaQuandoARespostaNaoPodeSerEntregue() {
        Long os = pagamentoPendente();
        String auth = "Bearer " + atendente();
        amqpAdmin.deleteQueue(FILA_RESPOSTAS); // a OS ainda nao declarou a fila: resposta nao roteada

        given().header("Authorization", auth).post("/pagamentos/" + os + "/aprovar").then().statusCode(500);

        given().header("Authorization", auth).get("/pagamentos/" + os).then().body("status", equalTo("CONFIRMADO"));

        declararFilaDeRespostas();
        given().header("Authorization", auth).post("/pagamentos/" + os + "/aprovar").then().statusCode(200);
        assertNotNull(receberResposta());
    }

    // ---- proteção dos endpoints ----

    @Test
    void shouldExigirAutenticacao() {
        given().post("/pagamentos/1/aprovar").then().statusCode(401);
        given().post("/pagamentos/1/recusar").then().statusCode(401);
        given().get("/pagamentos/1").then().statusCode(401);
    }

    @Test
    void shouldRejeitarTokenInvalido() {
        given().header("Authorization", "Bearer isso-nao-e-um-jwt")
                .post("/pagamentos/1/aprovar").then().statusCode(401);
    }

    @Test
    void shouldNegarPerfisSemPermissaoParaDecidirOuConsultar() {
        for (String papel : new String[]{"ROLE_CLIENTE", "ROLE_MECANICO"}) {
            String auth = "Bearer " + token(papel);
            given().header("Authorization", auth).post("/pagamentos/1/aprovar").then().statusCode(403);
            given().header("Authorization", auth).post("/pagamentos/1/recusar").then().statusCode(403);
            given().header("Authorization", auth).get("/pagamentos/1").then().statusCode(403);
        }
    }

    @Test
    void shouldNegarRotasNaoMapeadas() {
        given().header("Authorization", "Bearer " + atendente()).get("/qualquer-coisa").then().statusCode(403);
    }

    @Test
    void shouldLiberarOSwaggerEAProbeSemToken() {
        given().get("/v3/api-docs").then().statusCode(200);
        given().contentType(ContentType.JSON).get("/actuator/health/liveness").then().statusCode(200);
    }
}
