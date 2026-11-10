package com.fiap.cobranca.infra;

import com.fiap.cobranca.core.gateway.GatewayDePagamento;
import com.fiap.cobranca.core.gateway.PagamentoEventosGateway;
import com.fiap.cobranca.core.gateway.PagamentoGateway;
import com.fiap.cobranca.core.usecase.ConsultarPagamentoUseCase;
import com.fiap.cobranca.core.usecase.DecidirPagamentoUseCase;
import com.fiap.cobranca.core.usecase.SolicitarPagamentoUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Liga os casos de uso (Java puro) às implementações de infraestrutura. */
@Configuration
public class CobrancaConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SolicitarPagamentoUseCase solicitarPagamentoUseCase(PagamentoGateway pagamentos, GatewayDePagamento meio, Clock clock) {
        return new SolicitarPagamentoUseCase(pagamentos, meio, clock);
    }

    @Bean
    DecidirPagamentoUseCase decidirPagamentoUseCase(PagamentoGateway pagamentos, PagamentoEventosGateway eventos, Clock clock) {
        return new DecidirPagamentoUseCase(pagamentos, eventos, clock);
    }

    @Bean
    ConsultarPagamentoUseCase consultarPagamentoUseCase(PagamentoGateway pagamentos) {
        return new ConsultarPagamentoUseCase(pagamentos);
    }
}
