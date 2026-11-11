package com.fiap.cobranca.infra.controller.presenter;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.usecase.DecidirPagamentoOutputPort;
import com.fiap.cobranca.infra.controller.json.PagamentoResponseJson;

public class DecidirPagamentoPresenter implements DecidirPagamentoOutputPort {

    private PagamentoResponseJson viewModel;

    @Override
    public void apresentar(Pagamento pagamento) {
        this.viewModel = new PagamentoResponseJson(pagamento.ordemServicoId(), pagamento.clienteId(), pagamento.valor(),
                pagamento.status().name(), pagamento.criadoEm(), pagamento.atualizadoEm());
    }

    public PagamentoResponseJson getViewModel() {
        return viewModel;
    }
}
