package com.fiap.cobranca.core.gateway;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.domain.ResultadoCobranca;

/**
 * Porta para o meio de pagamento. Os casos de uso só sabem que a cobrança foi iniciada e o que o meio respondeu; quem
 * decide e como (mock HTTP hoje, Mercado Pago depois) é detalhe de infraestrutura.
 */
public interface GatewayDePagamento {

    /**
     * Inicia a cobrança junto ao meio de pagamento. Um meio síncrono já devolve a decisão ({@code APROVADO} ou
     * {@code RECUSADO}); um assíncrono (ou indisponível) devolve {@code PENDENTE} e a decisão chega depois.
     */
    ResultadoCobranca iniciarCobranca(Pagamento pagamento);
}
