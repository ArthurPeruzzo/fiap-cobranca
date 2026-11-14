package com.fiap.cobranca.resources;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Sobe um DynamoDB Local e um RabbitMQ de verdade (uma vez por execução) e o contexto completo da aplicação.
 * Todas as subclasses compartilham o mesmo contexto do Spring. A porta é livre mas definida antes da partida, porque o
 * meio de pagamento ({@code GatewayPagamentoHttp}) chama o mock deste mesmo serviço em {@code localhost:${server.port}}.
 */
@ActiveProfiles("integration-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public abstract class AbstractIntegrationTest {

    protected static final String SEGREDO = "fIACMIxZHpR5rhnyNTLfwJZOgDtZZVweeS1eRWuT6IZ_U5cRrinFFHJAeSHMpvPKHG_KLM-JTelvD5s3ulA";

    private static final GenericContainer<?> dynamo = new GenericContainer<>("amazon/dynamodb-local:latest")
            .withExposedPorts(8000)
            .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb")
            .waitingFor(Wait.forListeningPort());
    private static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.1-management");
    protected static final int PORTA = portaLivre();

    static {
        dynamo.start();
        rabbit.start();
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORTA);
        registry.add("resilience4j.retry.instances.gatewayPagamento.wait-duration", () -> "10ms");
        registry.add("cobranca.dynamodb.endpoint", AbstractIntegrationTest::endpointDoDynamo);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.virtual-host", () -> "/");
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    private static int portaLivre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static String endpointDoDynamo() {
        return "http://" + dynamo.getHost() + ":" + dynamo.getMappedPort(8000);
    }

    protected static String hostDoRabbit() {
        return rabbit.getHost();
    }

    protected static int portaDoRabbit() {
        return rabbit.getAmqpPort();
    }

    /** JWT no formato emitido pela API principal (subject = id do usuário, claim "roles"). */
    protected static String token(String... roles) {
        return JWT.create()
                .withIssuer("mecanica-fiap")
                .withSubject("1")
                .withArrayClaim("roles", roles)
                .withExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .sign(Algorithm.HMAC256(Base64.getUrlDecoder().decode(SEGREDO)));
    }
}
