package com.fiap.cobranca.core.gateway;

import com.fiap.cobranca.core.domain.Pagamento;

/**
 * Porta para o meio de pagamento. Os casos de uso só sabem que a cobrança foi iniciada e que depois alguém decide
 * (aprovar ou recusar); quem decide e como (simulação, webhook do Mercado Pago) é detalhe de infraestrutura.
 */
public interface GatewayDePagamento {

    /** Inicia a cobrança junto ao meio de pagamento. O pagamento segue PENDENTE até a decisão. */
    void iniciarCobranca(Pagamento pagamento);
}
