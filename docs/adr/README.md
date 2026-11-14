# ADRs — fiap-cobranca

Decisões arquiteturais deste serviço. As decisões do ecossistema (saga de pagamento orquestrada, token de serviço, mensageria com
outbox) estão nos ADRs do repositório `fiap-mecanica`, em especial o
[ADR-0009](https://github.com/ArthurPeruzzo/fiap-mecanica/blob/main/docs/adr/0009-saga-de-pagamento-orquestrada.md).

| ADR | Decisão | Status |
|---|---|---|
| [0001](0001-infraestrutura-propria-e-dynamodb.md) | Infraestrutura própria da cobrança: DynamoDB, imagem, manifestos e pipelines no repositório | Aceito |
| [0002](0002-mock-do-meio-de-pagamento.md) | Mock como meio de pagamento (secret → token → pagamento) e adapter HTTP com Retry e CircuitBreaker | Aceito |
