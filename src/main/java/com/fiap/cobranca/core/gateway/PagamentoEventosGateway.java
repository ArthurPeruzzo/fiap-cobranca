package com.fiap.cobranca.core.gateway;

/** Respostas da cobrança ao orquestrador (fiap-mecanica). Só retorna depois da entrega confirmada pelo broker. */
public interface PagamentoEventosGateway {

    void pagamentoConfirmado(Long ordemServicoId);

    void pagamentoRecusado(Long ordemServicoId);
}
