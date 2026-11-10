output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "dynamodb_table_name" {
  value = aws_dynamodb_table.pagamento.name
}

# Passthrough do state do cluster — consumido pelo cd.yml (kubectl).
output "eks_cluster_name" {
  value = data.terraform_remote_state.k8s.outputs.eks_cluster_name
}
