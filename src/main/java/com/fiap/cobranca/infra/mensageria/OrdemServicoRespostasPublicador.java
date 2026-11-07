package com.fiap.cobranca.infra.mensageria;

import com.fiap.cobranca.core.gateway.PagamentoEventosGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Responde ao orquestrador (fiap-mecanica) no exchange {@code ordemservico.eventos}. Só retorna depois que o broker
 * confirma que a mensagem chegou a uma fila; sem confirmação, ou sem fila ligada à chave, lança exceção.
 * <p>
 * Sem outbox (risco aceito): o status já está gravado no DynamoDB e quem decide o pagamento pode repetir a decisão,
 * o que reenvia a resposta.
 */
@Component
@Slf4j
public class OrdemServicoRespostasPublicador implements PagamentoEventosGateway {

    static final String TIPO_PAGAMENTO_CONFIRMADO = "PAGAMENTO_CONFIRMADO";
    static final String TIPO_PAGAMENTO_RECUSADO = "PAGAMENTO_RECUSADO";

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKeyConfirmado;
    private final String routingKeyRecusado;
    private final Duration timeoutDaConfirmacao;

    public OrdemServicoRespostasPublicador(RabbitTemplate rabbitTemplate,
                                           @Value("${ordemservico.mensageria.exchange}") String exchange,
                                           @Value("${ordemservico.mensageria.routing-key.pagamento-confirmado}") String routingKeyConfirmado,
                                           @Value("${ordemservico.mensageria.routing-key.pagamento-recusado}") String routingKeyRecusado,
                                           @Value("${cobranca.mensageria.timeout-confirmacao:5s}") Duration timeoutDaConfirmacao) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKeyConfirmado = routingKeyConfirmado;
        this.routingKeyRecusado = routingKeyRecusado;
        this.timeoutDaConfirmacao = timeoutDaConfirmacao;
    }

    @Override
    public void pagamentoConfirmado(Long ordemServicoId) {
        publicar(TIPO_PAGAMENTO_CONFIRMADO, routingKeyConfirmado, ordemServicoId);
    }

    @Override
    public void pagamentoRecusado(Long ordemServicoId) {
        publicar(TIPO_PAGAMENTO_RECUSADO, routingKeyRecusado, ordemServicoId);
    }

    private void publicar(String tipo, String routingKey, Long ordemServicoId) {
        String id = UUID.randomUUID().toString();
        Message mensagem = MessageBuilder
                .withBody(("{\"ordemServicoId\":" + ordemServicoId + "}").getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(id)
                .setType(tipo)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        CorrelationData correlacao = new CorrelationData(id);
        rabbitTemplate.send(exchange, routingKey, mensagem, correlacao);
        try {
            CorrelationData.Confirm confirmacao = correlacao.getFuture().get(timeoutDaConfirmacao.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirmacao.ack()) {
                throw new IllegalStateException("broker recusou a resposta " + tipo + ": " + confirmacao.reason());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrompido ao esperar a confirmacao do broker", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("sem confirmacao do broker para a resposta " + tipo, e);
        }
        if (correlacao.getReturned() != null) {
            // Confirmada pelo broker, mas sem fila ligada à chave (a OS ainda não declarou a fila de respostas).
            throw new IllegalStateException("resposta " + tipo + " nao roteada: " + correlacao.getReturned().getReplyText());
        }
        log.info("Resposta {} da ordem de servico {} publicada em {}", tipo, ordemServicoId, exchange);
    }
}
