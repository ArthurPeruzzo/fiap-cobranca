package com.fiap.cobranca.infra.gateway;

import com.fiap.cobranca.core.domain.Pagamento;
import com.fiap.cobranca.core.domain.StatusPagamento;
import com.fiap.cobranca.core.gateway.PagamentoGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Pagamentos no DynamoDB: uma tabela, chave de partição {@code ordemServicoId} (um pagamento por OS). */
@Component
public class PagamentoDynamoGateway implements PagamentoGateway {

    static final String CHAVE = "ordemServicoId";

    private final DynamoDbClient dynamo;
    private final String tabela;

    public PagamentoDynamoGateway(DynamoDbClient dynamo, @Value("${cobranca.dynamodb.tabela}") String tabela) {
        this.dynamo = dynamo;
        this.tabela = tabela;
    }

    @Override
    public boolean criarSeNaoExistir(Pagamento pagamento) {
        try {
            dynamo.putItem(PutItemRequest.builder()
                    .tableName(tabela)
                    .item(Map.of(
                            CHAVE, numero(pagamento.ordemServicoId()),
                            "clienteId", numero(pagamento.clienteId()),
                            "valor", AttributeValue.fromN(pagamento.valor().toPlainString()),
                            "status", AttributeValue.fromS(pagamento.status().name()),
                            "criadoEm", AttributeValue.fromS(pagamento.criadoEm().toString()),
                            "atualizadoEm", AttributeValue.fromS(pagamento.atualizadoEm().toString())))
                    .conditionExpression("attribute_not_exists(" + CHAVE + ")")
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public Optional<Pagamento> buscar(Long ordemServicoId) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(tabela)
                .key(Map.of(CHAVE, numero(ordemServicoId)))
                .consistentRead(true)
                .build()).item();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Pagamento(
                Long.valueOf(item.get(CHAVE).n()),
                Long.valueOf(item.get("clienteId").n()),
                new BigDecimal(item.get("valor").n()),
                StatusPagamento.valueOf(item.get("status").s()),
                Instant.parse(item.get("criadoEm").s()),
                Instant.parse(item.get("atualizadoEm").s())));
    }

    @Override
    public boolean transicionar(Long ordemServicoId, StatusPagamento de, StatusPagamento para, Instant quando) {
        try {
            dynamo.updateItem(UpdateItemRequest.builder()
                    .tableName(tabela)
                    .key(Map.of(CHAVE, numero(ordemServicoId)))
                    .updateExpression("SET #s = :para, atualizadoEm = :quando")
                    .conditionExpression("#s = :de")
                    .expressionAttributeNames(Map.of("#s", "status"))
                    .expressionAttributeValues(Map.of(
                            ":de", AttributeValue.fromS(de.name()),
                            ":para", AttributeValue.fromS(para.name()),
                            ":quando", AttributeValue.fromS(quando.toString())))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    private static AttributeValue numero(Long valor) {
        return AttributeValue.fromN(valor.toString());
    }
}
