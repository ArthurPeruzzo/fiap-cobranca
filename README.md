# fiap-cobranca

Microsserviço de cobrança da Mecânica FIAP (Fase 4).

## O que o serviço faz

Participa da saga de pagamento da ordem de serviço (orquestrada pelo `fiap-mecanica`, ver ADR-0009 lá):

1. Consome o comando `pagamento.solicitar` (fila `cobranca.solicitar-pagamento`, exchange `cobranca.comandos`) com
   `{ordemServicoId, clienteId, valor}` e grava o pagamento **PENDENTE** no DynamoDB (escrita condicional: repetir a mensagem
   não cria outro).
2. Alguém decide o pagamento (aprovar ou recusar). A cobrança grava o status e responde no exchange `ordemservico.eventos`
   com `pagamento.confirmado` ou `pagamento.recusado` (`{"ordemServicoId":N}`), só depois da confirmação do broker.

Regra de negócio e meio de pagamento são separados: os casos de uso só conhecem a porta `GatewayDePagamento`
(`iniciarCobranca`) e `DecidirPagamentoUseCase`. Hoje o meio é **simulado** (`GatewayDePagamentoSimulado`); um meio real
(Mercado Pago) entra como outro bean da porta, chamando o mesmo `DecidirPagamentoUseCase` a partir do seu webhook.

### Endpoints

Todos exigem JWT da API principal (mesmo segredo/issuer) com perfil `ATENDENTE` ou `ADMINISTRADOR`; qualquer outra rota é negada.

| Método | Rota | O que faz |
|---|---|---|
| GET | `/pagamentos/{ordemServicoId}` | consulta o pagamento |
| POST | `/pagamentos/{ordemServicoId}/aprovar` | decisão simulada: confirma e avisa a OS |
| POST | `/pagamentos/{ordemServicoId}/recusar` | decisão simulada: recusa e avisa a OS |

`aprovar`/`recusar` só existem com `cobranca.pagamento.simulado.enabled=true` (padrão). Repetir a mesma decisão reenvia a
resposta à OS (útil se a publicação falhou); decisão contrária à já gravada responde 409.

### Falhas e riscos aceitos

- Comando malformado → DLQ direto. Falha de infraestrutura → retry (3x, 1s/2s/4s) e depois `cobranca.solicitar-pagamento.dlq`.
- Gravação no DynamoDB e publicação da resposta **não são atômicas** (sem outbox): se a publicação falha, o endpoint responde
  500 e basta repetir a decisão.
- Pagamento PENDENTE que nunca é decidido deixa a OS em `AGUARDANDO_PAGAMENTO` (sem expiração).

### Configuração

| Variável | Para quê |
|---|---|
| `JWT_SECRET` | segredo do JWT (Base64URL), o mesmo do `fiap-mecanica` |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` / `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_VHOST` | broker (o usuário `cobranca` é criado pelo CD do `fiap-estoque`) |
| `AWS_REGION`, `DYNAMODB_TABELA` | DynamoDB (credenciais pela cadeia padrão da AWS) |
| `DYNAMODB_ENDPOINT`, `DYNAMODB_CRIAR_TABELA=true` | só local: aponta para o DynamoDB Local e cria a tabela |

Chave da tabela: `ordemServicoId` (número). Testes: `./mvnw verify` (precisa de Docker: sobe DynamoDB Local e RabbitMQ).
