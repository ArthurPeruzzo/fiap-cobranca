package com.fiap.cobranca.infra.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Cria a tabela se não existir. Só para desenvolvimento local (DynamoDB Local); na AWS a tabela é criada pela
 * infraestrutura e esta propriedade fica desligada.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "cobranca.dynamodb.criar-tabela", havingValue = "true")
public class TabelaDePagamentosInicializador implements ApplicationRunner {

    private final DynamoDbClient dynamo;
    private final String tabela;

    public TabelaDePagamentosInicializador(DynamoDbClient dynamo, @Value("${cobranca.dynamodb.tabela}") String tabela) {
        this.dynamo = dynamo;
        this.tabela = tabela;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            dynamo.createTable(builder -> builder
                    .tableName(tabela)
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName(PagamentoDynamoGateway.CHAVE).attributeType(ScalarAttributeType.N).build())
                    .keySchema(KeySchemaElement.builder()
                            .attributeName(PagamentoDynamoGateway.CHAVE).keyType(KeyType.HASH).build())
                    .billingMode(BillingMode.PAY_PER_REQUEST));
            log.info("Tabela {} criada", tabela);
        } catch (ResourceInUseException e) {
            log.info("Tabela {} ja existe", tabela);
        }
    }
}
