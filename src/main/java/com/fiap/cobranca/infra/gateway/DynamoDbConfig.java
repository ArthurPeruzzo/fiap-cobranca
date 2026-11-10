package com.fiap.cobranca.infra.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

@Configuration
public class DynamoDbConfig {

    /**
     * Na AWS usa a cadeia padrão de credenciais (role do pod). Com {@code cobranca.dynamodb.endpoint} (DynamoDB Local,
     * desenvolvimento e testes) aponta para ele com credenciais fictícias.
     */
    @Bean(destroyMethod = "close")
    DynamoDbClient dynamoDbClient(@Value("${cobranca.dynamodb.region}") String regiao,
                                  @Value("${cobranca.dynamodb.endpoint:}") String endpoint) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder().region(Region.of(regiao));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
        }
        return builder.build();
    }
}
