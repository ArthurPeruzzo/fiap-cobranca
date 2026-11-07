package com.fiap.cobranca.infra.mensageria;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Garante a falha na partida quando RABBITMQ_USERNAME/RABBITMQ_PASSWORD não foram definidos: o binder deixaria o
 * placeholder como texto literal; o {@code @Value} resolve placeholders aninhados e lança a exceção.
 */
@Component
public class CredenciaisDoBrokerObrigatorias {

    public CredenciaisDoBrokerObrigatorias(@Value("${spring.rabbitmq.username}") String username,
                                           @Value("${spring.rabbitmq.password}") String password) {
        // a validação é a própria resolução dos valores acima
    }
}
