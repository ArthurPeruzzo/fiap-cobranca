# ADR-0002 — Mock do meio de pagamento com adapter HTTP resiliente

## Contexto

A cobrança precisava mostrar a integração com um meio de pagamento externo e o comportamento da saga diante de falhas
dele, sem depender do Mercado Pago real. O professor aprovou um mock simples (secret → token → pagamento) com um
contador de chamadas que alterna sucesso e falha; a lógica do mock em si não é critério de avaliação. A decisão manual
(`/pagamentos/{os}/aprovar|recusar`) e a arquitetura (portas em `core/`, ADR-0009 do `fiap-mecanica`) tinham de continuar valendo.

## Decisão

1. **A porta devolve o resultado.** `GatewayDePagamento.iniciarCobranca` passa a devolver `ResultadoCobranca`
   (`APROVADO`, `RECUSADO`, `PENDENTE`). `SolicitarPagamentoUseCase` encaminha aprovado/recusado ao mesmo
   `DecidirPagamentoUseCase` da decisão manual (grava o status e avisa a OS); `PENDENTE` deixa a decisão para depois
   (manual ou, no Mercado Pago real, o webhook).
2. **Um adapter só, sem chaves de configuração**: `GatewayPagamentoHttp` é a implementação da porta e o mock é o meio de
   pagamento do fluxo (o antigo gateway simulado, que não chamava ninguém, foi removido). Outro meio (Mercado Pago) entra
   depois como outra implementação da porta, sem mexer nos casos de uso. O adapter obtém o token com
   `client_id`/`client_secret`, guarda-o até 10s antes de expirar e o renova uma vez quando recebe 401. URL e credenciais vêm de
   `PAGAMENTO_URL`, `PAGAMENTO_CLIENT_ID` e `PAGAMENTO_CLIENT_SECRET` (padrão: o mock em `localhost:${server.port}`).
3. **Resiliência com Resilience4j** (`resilience4j-spring-boot4`), instância `gatewayPagamento`:
   - `@Retry` (externo): 3 tentativas, backoff exponencial a partir de 200ms, só para 5xx e erro de rede.
   - `@CircuitBreaker` (interno, cada tentativa conta): janela de 8 chamadas, abre com 75% de falhas, fica aberto 30s.
   - Fallback no retry: tentativas esgotadas, circuito aberto ou erro 4xx deixam o pagamento **PENDENTE**; a equipe ainda
     pode decidi-lo à mão.
4. **Mock no próprio serviço** (`infra/mockpagamento`, `/mock-pagamentos`), sempre ligado. Faz a própria autenticação (o
   filtro JWT ignora essa rota), como um meio externo.
   - `POST /mock-pagamentos/oauth/token` `{client_id, client_secret, grant_type}` → `{access_token, expires_in: 300}`; credencial errada → 401.
   - `POST /mock-pagamentos/pagamentos` (Bearer) `{ordemId, valor}` → `{id, status}`; sem token válido → 401.
   - Contador de chamadas `n % 4`: 1 e 2 → **503**, 3 → **APROVADO**, 4 → **RECUSADO**, e recomeça. Com o retry, o 1º pagamento
     é aprovado na 3ª tentativa, o 2º é recusado (dispara a compensação da saga: libera a reserva e cancela a OS), e assim por diante.
   - Valor terminado em **.99** é sempre recusado, sem passar pelo contador (útil para testes e demonstração).

## Consequências

- A saga completa roda sozinha: o comando chega, o meio decide e a OS recebe a resposta, sem aprovação manual.
- O ciclo normal do mock tem 50% de falhas, abaixo do limite do circuito; ele só abre quando o meio cai de vez (URL
  errada, porta fechada). Para demonstrar: suba com `PAGAMENTO_URL` apontando para uma porta fechada e envie alguns comandos.
- O mock guarda contador e tokens em memória: com mais de uma réplica cada pod tem o seu ciclo. É uma ferramenta de
  demonstração, e `/mock-pagamentos` fica exposto no Service (protegido só pelas credenciais do mock).
- Risco aceito (o mesmo da decisão manual): gravação do status e publicação da resposta não são atômicas. Se a
  publicação falhar depois do meio aprovar, a mensagem repetida não refaz a cobrança (o pagamento já existe) e a decisão
  pode ser reenviada pelo endpoint manual.
