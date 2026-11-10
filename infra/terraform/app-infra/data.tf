# Leitura somente-leitura do state do repositorio fiap-mecanica-infra-k8s — este modulo nunca escreve la.
# So o nome do cluster EKS e usado (para o kubectl do CD): a cobranca nao tem banco em VPC (DynamoDB e um servico
# regional acessado por endpoint publico da AWS), entao nao precisa de subnets nem de security group.
data "terraform_remote_state" "k8s" {
  backend = "s3"
  config = {
    bucket = "fiap-mecanica"
    key    = "tfstate/terraform.tfstate"
    region = "us-east-1"
  }
}
