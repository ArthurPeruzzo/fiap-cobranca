# Banco PROPRIO da cobranca (NoSQL, requisito da Fase 4): uma tabela com um item por ordem de servico.
# A chave e o id da OS, o que torna a criacao do pagamento idempotente (put condicional) e a decisao do pagamento
# uma escrita condicional por status. Sob demanda: sem capacidade para dimensionar.
#
# Acesso dos pods: a Academy Lab so tem a LabRole, entao nao ha role dedicada (IRSA); os pods usam as credenciais da
# LabRole dos nos pelo IMDS (o CD do fiap-estoque ja ajusta o hop limit para 2). A LabRole precisa poder usar o DynamoDB.
resource "aws_dynamodb_table" "pagamento" {
  name         = var.dynamodb_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "ordemServicoId"

  attribute {
    name = "ordemServicoId"
    type = "N"
  }

  tags = var.tags
}
