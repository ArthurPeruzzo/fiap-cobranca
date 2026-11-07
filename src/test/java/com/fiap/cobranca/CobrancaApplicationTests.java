package com.fiap.cobranca;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CobrancaApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void mainSobeAAplicacao() {
		// Porta aleatoria para nao colidir com o contexto do @SpringBootTest nem com o 8082 local.
		assertThatCode(() -> CobrancaApplication.main(new String[] {"--server.port=0"}))
				.doesNotThrowAnyException();
	}

}
