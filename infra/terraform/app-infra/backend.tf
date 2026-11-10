terraform {
  # use_lockfile (lock de state nativo do S3, sem DynamoDB) exige Terraform >= 1.10.
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Bucket compartilhado pelos repositorios do projeto (criado pelo CD do fiap-mecanica-infra-k8s);
  # a chave e propria deste servico, entao um apply aqui nunca toca o state dos outros.
  backend "s3" {
    bucket = "fiap-mecanica"
    key    = "tfstate/cobranca.tfstate"
    region = "us-east-1"
    # Lock de state nativo do S3: dois `apply` na mesma chave nao se sobrescrevem
    # (ex.: apply local sobrepondo o CD). Objeto <chave>.tflock no mesmo bucket.
    use_lockfile = true
  }
}
