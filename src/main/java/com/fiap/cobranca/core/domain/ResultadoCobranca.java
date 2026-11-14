package com.fiap.cobranca.core.domain;

/**
 * O que o meio de pagamento respondeu ao iniciar a cobrança. {@code PENDENTE} quer dizer que a decisão vem depois
 * (decisão manual, webhook ou meio de pagamento indisponível).
 */
public enum ResultadoCobranca {
    APROVADO,
    RECUSADO,
    PENDENTE
}
