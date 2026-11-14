package com.fiap.cobranca.infra.mensageria;

import com.fiap.cobranca.core.dto.SolicitarPagamentoDto;
import com.fiap.cobranca.core.gateway.GatewayDePagamento;
import com.fiap.cobranca.core.gateway.PagamentoEventosGateway;
import com.fiap.cobranca.core.gateway.PagamentoGateway;
import com.fiap.cobranca.infra.controller.PagamentoCleanController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Consome o comando de pagamento. Entrega "pelo menos uma vez": o pagamento é criado com escrita condicional, então a
 * repetição não cria outro. Falha de infraestrutura propaga e aciona o retry; esgotado, a mensagem vai para a DLQ.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "cobranca.mensageria.listener.enabled", havingValue = "true", matchIfMissing = true)
public class SolicitarPagamentoListener {

    private final PagamentoCleanController cleanController;

    public SolicitarPagamentoListener(PagamentoGateway pagamentoGateway, PagamentoEventosGateway eventosGateway,
                                      GatewayDePagamento gatewayDePagamento, Clock clock) {
        this.cleanController = new PagamentoCleanController(pagamentoGateway, eventosGateway, gatewayDePagamento, clock);
    }

    @RabbitListener(queues = "${cobranca.mensageria.fila.solicitar-pagamento}")
    public void solicitar(SolicitarPagamentoComando comando) {
        if (comando == null) {
            throw new AmqpRejectAndDontRequeueException("Comando de pagamento vazio");
        }
        boolean criado;
        try {
            criado = cleanController.solicitar(new SolicitarPagamentoDto(comando.ordemServicoId(), comando.clienteId(), comando.valor()));
        } catch (IllegalArgumentException e) {
            // Mensagem malformada nunca vai passar numa nova tentativa: segue direto para a DLQ.
            throw new AmqpRejectAndDontRequeueException("Comando de pagamento invalido: " + e.getMessage(), e);
        }
        if (criado) {
            log.info("Pagamento da ordem de servico {} registrado e enviado ao meio de pagamento", comando.ordemServicoId());
        } else {
            log.info("Comando repetido: a ordem de servico {} ja tem pagamento", comando.ordemServicoId());
        }
    }
}
