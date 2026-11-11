package com.fiap.cobranca.infra.controller;

import com.fiap.cobranca.core.dto.SolicitarPagamentoDto;
import com.fiap.cobranca.core.gateway.GatewayDePagamento;
import com.fiap.cobranca.core.gateway.PagamentoEventosGateway;
import com.fiap.cobranca.core.gateway.PagamentoGateway;
import com.fiap.cobranca.core.usecase.ConsultarPagamentoUseCase;
import com.fiap.cobranca.core.usecase.DecidirPagamentoUseCase;
import com.fiap.cobranca.core.usecase.SolicitarPagamentoUseCase;
import com.fiap.cobranca.infra.controller.json.PagamentoResponseJson;
import com.fiap.cobranca.infra.controller.presenter.ConsultarPagamentoPresenter;
import com.fiap.cobranca.infra.controller.presenter.DecidirPagamentoPresenter;

import java.time.Clock;

public class PagamentoCleanController {

    private final PagamentoGateway pagamentoGateway;
    private final PagamentoEventosGateway eventosGateway;
    private final GatewayDePagamento gatewayDePagamento;
    private final Clock clock;

    public PagamentoCleanController(PagamentoGateway pagamentoGateway, PagamentoEventosGateway eventosGateway,
                                    GatewayDePagamento gatewayDePagamento, Clock clock) {
        this.pagamentoGateway = pagamentoGateway;
        this.eventosGateway = eventosGateway;
        this.gatewayDePagamento = gatewayDePagamento;
        this.clock = clock;
    }

    /** @return true se criou o pagamento; false se já existia (mensagem duplicada). */
    public boolean solicitar(SolicitarPagamentoDto dto) {
        return new SolicitarPagamentoUseCase(pagamentoGateway, gatewayDePagamento, clock).solicitar(dto);
    }

    public PagamentoResponseJson consultar(Long ordemServicoId) {
        var presenter = new ConsultarPagamentoPresenter();
        new ConsultarPagamentoUseCase(pagamentoGateway, presenter).consultar(ordemServicoId);
        return presenter.getViewModel();
    }

    public PagamentoResponseJson confirmar(Long ordemServicoId) {
        var presenter = new DecidirPagamentoPresenter();
        new DecidirPagamentoUseCase(pagamentoGateway, eventosGateway, clock, presenter).confirmar(ordemServicoId);
        return presenter.getViewModel();
    }

    public PagamentoResponseJson recusar(Long ordemServicoId) {
        var presenter = new DecidirPagamentoPresenter();
        new DecidirPagamentoUseCase(pagamentoGateway, eventosGateway, clock, presenter).recusar(ordemServicoId);
        return presenter.getViewModel();
    }
}
