package com.fiap.cobranca.infra.gateway;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.gateway.GatewayDePagamento;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Meio de pagamento simulado: não chama ninguém. O pagamento fica PENDENTE até a equipe aprová-lo ou recusá-lo pelos
 * endpoints de {@code /pagamentos}. Uma implementação real (Mercado Pago) entra como outro bean desta porta.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "cobranca.pagamento.simulado.enabled", havingValue = "true", matchIfMissing = true)
public class GatewayDePagamentoSimulado implements GatewayDePagamento {

    @Override
    public void iniciarCobranca(Pagamento pagamento) {
        log.info("Pagamento simulado da ordem de servico {} (valor {}) aguardando decisao", pagamento.ordemServicoId(), pagamento.valor());
    }
}
