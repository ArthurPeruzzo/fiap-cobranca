variable "project_name" {
  default = "fiap-cobranca"
}

variable "region_default" {
  default = "us-east-1"
}

variable "tags" {
  default = {
    Name = "fiap-cobranca-terraform"
  }
}

variable "dynamodb_table_name" {
  description = "Nome da tabela DynamoDB de pagamentos (deve ser o mesmo DYNAMODB_TABELA do servico)"
  default     = "cobranca-pagamento"
}
