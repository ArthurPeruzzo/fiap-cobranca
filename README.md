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

### Papel na saga

A saga de pagamento é **orquestrada pelo `fiap-mecanica`** (decisão e justificativa no
[ADR-0009](../fiap-mecanica/docs/adr/0009-saga-de-pagamento-orquestrada.md)): ele grava o comando de pagamento na outbox e reage às
respostas. A cobrança é **participante**: recebe o comando, guarda o pagamento e responde `pagamento.confirmado` ou
`pagamento.recusado`. Cobra-se primeiro e baixa-se o estoque depois, então a compensação de um pagamento recusado é só liberar
a reserva e cancelar a OS; não existe estorno.

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

## Arquitetura

Clean Architecture, igual ao `fiap-estoque` e ao `fiap-mecanica`: `core/` (domínio, casos de uso e portas) sem Spring nem AWS, e
`infra/` com os detalhes. Pacote raiz `com.fiap.cobranca`.

- `core/domain`: `Pagamento` e `StatusPagamento` (`PENDENTE`, `CONFIRMADO`, `RECUSADO`).
- `core/gateway`: portas `PagamentoGateway` (persistência), `PagamentoEventosGateway` (resposta à OS) e `GatewayDePagamento` (meio de pagamento).
- `core/usecase`: `SolicitarPagamentoUseCase`, `ConsultarPagamentoUseCase` e `DecidirPagamentoUseCase`, com `OutputPort` para devolver o resultado.
- `core/dto`: dados de entrada dos casos de uso.
- `infra/controller`: `PagamentoHttpController` e `PagamentoSimuladoHttpController` (Spring, rotas HTTP) chamam o `PagamentoCleanController`
  (Java puro), que monta os casos de uso e devolve o resultado por *presenter* (`infra/controller/presenter`) em um `json/PagamentoResponseJson`.
- `infra/mensageria`: listener do comando (`SolicitarPagamentoListener`, que usa o mesmo `PagamentoCleanController`) e publicador das respostas.
- `infra/gateway`: DynamoDB (`PagamentoDynamoGateway`) e o meio de pagamento simulado.
- `shared`: exceções, segurança JWT e OpenAPI.

## Testes e cobertura

`./mvnw verify` roda os testes unitários e os de integração (Testcontainers: DynamoDB Local e RabbitMQ, inclui o fluxo completo da
saga de pagamento), gera o relatório do JaCoCo e **falha abaixo de 80% de linhas**. Medição de 10/11/2026: **47 testes, 0 falhas,
97,7% das linhas cobertas** (297/304). Relatório em `target/site/jacoco/index.html`; no CI o resumo aparece na execução e o
relatório HTML é publicado como artefato (`jacoco-report`).

## Infraestrutura e deploy

Segue o molde do `fiap-estoque` (ver `docs/adr/0001-infraestrutura-propria-e-dynamodb.md`):

- `Dockerfile` e `docker-compose.yml` (a cobrança + DynamoDB Local; o broker sobe no compose do `fiap-mecanica`):
  `cp .env.example .env`, preencher `JWT_SECRET` e a senha do usuário `cobranca`, `docker compose up --build`.
- `infra/terraform/app-infra`: ECR e tabela DynamoDB (`cobranca-pagamento`), state próprio no bucket `fiap-mecanica`.
- `k8s/`: namespace `fiap-cobranca`, Deployment (porta 8082), Service `LoadBalancer` e HPA.
- `.github/workflows`: `ci.yml` (PR: testes, cobertura de 80%, Sonar, build da imagem, `terraform plan`) e `cd.yml` (main: Terraform →
  imagem no ECR → deploy). Os testes rodam só no CI: a `main` é protegida e só recebe merge de PR com o check `build-test` verde.
  Antes do primeiro deploy, rode o CD do `fiap-estoque`: ele cria o broker e o usuário `cobranca`.

| GitHub Secret | Para quê |
|---|---|
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` | Credenciais da Academy Lab (`scripts/refresh-aws-secrets.sh` as republica). |
| `JWT_SECRET` | O mesmo do `fiap-mecanica`. |
| `RABBITMQ_COBRANCA_PASSWORD` | O mesmo do `fiap-estoque` (que cria o usuário no broker). |
| `NEW_RELIC_LICENSE_KEY` | Observabilidade. |
| `SONAR_TOKEN` | Token do SonarCloud para a análise de qualidade no CI (ver abaixo). |

Os pods acessam o DynamoDB pelas credenciais da `LabRole` dos nós (IMDS), sem role dedicada.

## Qualidade de código (SonarCloud)

O `build-test` do CI (pull request) roda `./mvnw verify` — testes, relatório do JaCoCo e mínimo de 80% de cobertura — e depois a
análise do **SonarCloud** (plano gratuito, repositório público) sobre as classes e o relatório já gerados. O passo usa
`-Dsonar.qualitygate.wait=true`: se o Quality Gate falhar, o pipeline falha. A configuração está nas propriedades `sonar.*` do
`pom.xml` (projeto `ArthurPeruzzo_fiap-cobranca`).

Configuração única, fora do repositório: criar a organização e importar este repositório em <https://sonarcloud.io>, conferir a
*organization key* e a *project key* (devem bater com o `pom.xml`) e cadastrar o secret **`SONAR_TOKEN`** no repositório. Enquanto o
secret não existir (ou em PR de fork), o passo é pulado com um aviso e o restante do pipeline segue normalmente.

