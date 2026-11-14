package com.fiap.cobranca.infra.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Onde e com que credenciais o {@link GatewayPagamentoHttp} fala com o meio de pagamento (mock ou real). */
@ConfigurationProperties("cobranca.pagamento.http")
public record GatewayPagamentoHttpProperties(String url, String clientId, String clientSecret,
                                             Duration connectTimeout, Duration readTimeout) {
}
