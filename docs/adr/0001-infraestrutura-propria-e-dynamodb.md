# ADR-0001 — Infraestrutura própria da cobrança (DynamoDB, imagem, manifestos e pipelines)

## Contexto

A Fase 4 exige microsserviços com repositório, infraestrutura e banco próprios, pipeline independente de CI/CD e,
entre os serviços, ao menos um banco **NoSQL**. A cobrança é esse serviço: guarda um pagamento por ordem de serviço.
O molde de infraestrutura é o do `fiap-estoque` (ADR-0001 de lá), na mesma conta AWS Academy Lab, onde só existe a
`LabRole` (não dá para criar IAM roles).

## Decisão

1. **Cluster e rede compartilhados** (`fiap-mecanica-infra-k8s`); o que é da cobrança mora aqui: Terraform
   (`infra/terraform/app-infra`), manifestos (`k8s/`), Dockerfile e pipelines. O módulo só lê o nome do cluster por
   `terraform_remote_state`. State próprio (`tfstate/cobranca.tfstate`).
2. **DynamoDB** (`cobranca-pagamento`, sob demanda, chave `ordemServicoId`). A chave é o id da OS, o que dá idempotência
   (put condicional) e decisão atômica do status (update condicional). Não há subnet group nem security group: o
   DynamoDB é regional e acessado pelo endpoint da AWS.
3. **Acesso dos pods ao DynamoDB pela `LabRole` dos nós** (IMDS), sem role dedicada (IRSA) por limitação da Academy.
   O CD ajusta o hop limit do IMDS para 2, como o do estoque faz para o EBS CSI. Se a `LabRole` não puder usar o
   DynamoDB nesta conta, o pod falha na primeira leitura/escrita (pagamento nunca vai a `PENDENTE`). Verificado em
   10/11/2026: um pod assumiu a `LabRole` pelo IMDS e leu a tabela, e a saga completa funcionou pelo API Gateway.
4. **Exposição.** O Service é `LoadBalancer` para o API Gateway alcançar `/pagamentos/**` (a rota é criada pelo CD do
   `fiap-mecanica`, dono do gateway, quando o Service já existe). O que protege essas rotas é o JWT da API principal (perfil
   ATENDENTE ou ADMINISTRADOR); a saga em si (comando e resposta) passa só pelo broker.
5. **Broker.** Não é implantado aqui: o RabbitMQ do cluster é do CD do `fiap-estoque`, que cria o usuário `cobranca`
   (secret `RABBITMQ_COBRANCA_PASSWORD`, o mesmo nos dois repositórios). Ordem: CD do estoque antes do da cobrança.
6. **Pipelines.** `build-test` (testes com DynamoDB Local e RabbitMQ em Testcontainers, gate de 80% de linhas e análise do
   SonarCloud com Quality Gate), `ci` (pull request: build-test, build da imagem, `terraform fmt/validate/plan`) e `cd`
   (main: infra → imagem no ECR → deploy). Os testes rodam só no CI: a `main` exige PR atualizado, com os checks
   `build-test` e `terraform-plan` verdes, inclusive para administradores, então o que chega ao CD já foi testado.

## Consequências

- O serviço é implantável de forma independente, com banco, imagem e pipeline próprios.
- Depende da `LabRole` ter acesso ao DynamoDB e do hop limit 2 nos nós (ver 3).
- Sem a infra do broker aqui, um ambiente zerado exige o CD do estoque primeiro.
- O CD não repete os testes: a garantia vem da proteção da `main` (ver 6). Um push direto na `main` não seria testado, por isso a
  proteção vale também para administradores.
- O ELB público expõe `/pagamentos/**`; a proteção é só o JWT. Quando o Mercado Pago entrar, o webhook será outra rota,
  pública por natureza e protegida por assinatura.
