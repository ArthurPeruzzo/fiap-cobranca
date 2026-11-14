package com.fiap.cobranca.infra.gateway;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(GatewayPagamentoHttpProperties.class)
public class GatewayPagamentoHttpConfig {

    /** Parte do builder do Spring, que já propaga o trace (traceparent) para o meio de pagamento. */
    @Bean
    RestClient gatewayPagamentoRestClient(RestClient.Builder builder, GatewayPagamentoHttpProperties properties) {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build());
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.baseUrl(properties.url()).requestFactory(requestFactory).build();
    }
}
