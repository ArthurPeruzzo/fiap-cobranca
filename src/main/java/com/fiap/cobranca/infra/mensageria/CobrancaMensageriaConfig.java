package com.fiap.cobranca.infra.mensageria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia do lado do consumidor: a fila de comandos de pagamento, a DLQ para o que esgotar as tentativas e as
 * ligações. Os exchanges são declarados também pelo fiap-mecanica, com os mesmos argumentos.
 */
@Configuration
@ConditionalOnProperty(name = "cobranca.mensageria.listener.enabled", havingValue = "true", matchIfMissing = true)
public class CobrancaMensageriaConfig {

    @Bean
    DirectExchange cobrancaComandosExchange(@Value("${cobranca.mensageria.exchange}") String nome) {
        return new DirectExchange(nome, true, false);
    }

    @Bean
    DirectExchange cobrancaComandosDeadLetterExchange(@Value("${cobranca.mensageria.exchange}") String nome) {
        return new DirectExchange(nome + ".dlx", true, false);
    }

    @Bean
    Queue filaSolicitarPagamento(@Value("${cobranca.mensageria.fila.solicitar-pagamento}") String nome,
                                 @Value("${cobranca.mensageria.exchange}") String exchange,
                                 @Value("${cobranca.mensageria.routing-key.solicitar-pagamento}") String routingKey) {
        return QueueBuilder.durable(nome)
                .deadLetterExchange(exchange + ".dlx")
                .deadLetterRoutingKey(routingKey)
                .build();
    }

    @Bean
    Queue filaSolicitarPagamentoDlq(@Value("${cobranca.mensageria.fila.solicitar-pagamento}") String nome) {
        return QueueBuilder.durable(nome + ".dlq").build();
    }

    @Bean
    Binding bindingSolicitarPagamento(Queue filaSolicitarPagamento, DirectExchange cobrancaComandosExchange,
                                      @Value("${cobranca.mensageria.routing-key.solicitar-pagamento}") String routingKey) {
        return BindingBuilder.bind(filaSolicitarPagamento).to(cobrancaComandosExchange).with(routingKey);
    }

    @Bean
    Binding bindingSolicitarPagamentoDlq(Queue filaSolicitarPagamentoDlq, DirectExchange cobrancaComandosDeadLetterExchange,
                                         @Value("${cobranca.mensageria.routing-key.solicitar-pagamento}") String routingKey) {
        return BindingBuilder.bind(filaSolicitarPagamentoDlq).to(cobrancaComandosDeadLetterExchange).with(routingKey);
    }

    /** Exchange das respostas ao orquestrador. Dono: fiap-mecanica (declara fila e ligações); aqui só o exchange. */
    @Bean
    DirectExchange ordemServicoEventosExchange(@Value("${ordemservico.mensageria.exchange}") String nome) {
        return new DirectExchange(nome, true, false);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
