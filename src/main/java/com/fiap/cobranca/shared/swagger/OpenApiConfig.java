package com.fiap.cobranca.shared.swagger;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@OpenAPIDefinition(
        tags = @Tag(name = "Pagamento", description = "Consulta e decisão (simulada) dos pagamentos das ordens de serviço"),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    /** Quando {@code swagger.server-url} está vazio nada é sobrescrito e o springdoc deduz o servidor pelo Host. */
    @Bean
    public OpenApiCustomizer serverUrlCustomizer(@Value("${swagger.server-url:}") String serverUrl) {
        return openApi -> {
            if (!serverUrl.isBlank()) {
                openApi.setServers(List.of(new Server().url(serverUrl).description("API Gateway")));
            }
        };
    }
}
