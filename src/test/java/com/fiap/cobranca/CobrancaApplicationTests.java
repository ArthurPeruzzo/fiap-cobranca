package com.fiap.cobranca;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.fiap.cobranca.resources.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class CobrancaApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

	@Test
	void mainSobeAAplicacao() {
		// Porta aleatoria para nao colidir com o contexto do teste nem com o 8082 local; sem listener para nao
		// competir com o contexto do teste pelas mensagens da fila.
		assertThatCode(() -> CobrancaApplication.main(new String[] {
				"--server.port=0",
				"--spring.profiles.active=integration-test",
				"--cobranca.mensageria.listener.enabled=false",
				"--cobranca.dynamodb.endpoint=" + endpointDoDynamo(),
				"--spring.rabbitmq.host=" + hostDoRabbit(),
				"--spring.rabbitmq.port=" + portaDoRabbit(),
				"--spring.rabbitmq.virtual-host=/",
				"--spring.rabbitmq.username=guest",
				"--spring.rabbitmq.password=guest"}))
				.doesNotThrowAnyException();
	}

}
