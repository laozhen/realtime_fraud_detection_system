output "sqs_queue_url" {
  description = "URL of the SQS queue"
  value       = aws_sqs_queue.fraud_detection_queue.url
}

output "sqs_queue_arn" {
  description = "ARN of the SQS queue"
  value       = aws_sqs_queue.fraud_detection_queue.arn
}

output "sqs_queue_name" {
  description = "Name of the SQS queue"
  value       = aws_sqs_queue.fraud_detection_queue.name
}

output "sqs_dlq_url" {
  description = "URL of the SQS DLQ"
  value       = aws_sqs_queue.fraud_detection_dlq.url
}

output "fraud_detection_role_arn" {
  description = "ARN of the fraud detection service IAM role"
  value       = aws_iam_role.fraud_detection_role.arn
}

output "transaction_producer_role_arn" {
  description = "ARN of the transaction producer IAM role"
  value       = aws_iam_role.transaction_producer_role.arn
}

output "cloudwatch_log_group_fraud_detection" {
  description = "CloudWatch log group for fraud detection service"
  value       = var.enable_cloudwatch_logs ? aws_cloudwatch_log_group.fraud_detection_logs[0].name : ""
}

output "cloudwatch_log_group_transaction_producer" {
  description = "CloudWatch log group for transaction producer"
  value       = var.enable_cloudwatch_logs ? aws_cloudwatch_log_group.transaction_producer_logs[0].name : ""
}

output "region" {
  description = "AWS region"
  value       = var.aws_region
}

# EKS Cluster Outputs
output "eks_cluster_id" {
  description = "EKS cluster ID"
  value       = var.create_eks_cluster ? aws_eks_cluster.main[0].id : ""
}

output "eks_cluster_endpoint" {
  description = "EKS cluster endpoint"
  value       = var.create_eks_cluster ? aws_eks_cluster.main[0].endpoint : ""
}

output "eks_cluster_security_group_id" {
  description = "Security group ID attached to the EKS cluster"
  value       = var.create_eks_cluster ? aws_security_group.eks_cluster[0].id : ""
}

output "eks_cluster_arn" {
  description = "ARN of the EKS cluster"
  value       = var.create_eks_cluster ? aws_eks_cluster.main[0].arn : ""
}

output "eks_cluster_certificate_authority_data" {
  description = "Certificate authority data for the EKS cluster"
  value       = var.create_eks_cluster ? aws_eks_cluster.main[0].certificate_authority[0].data : ""
  sensitive   = true
}

output "eks_oidc_provider_arn" {
  description = "ARN of the OIDC provider for EKS"
  value       = var.create_eks_cluster ? aws_iam_openid_connect_provider.eks[0].arn : ""
}

output "eks_vpc_id" {
  description = "VPC ID for EKS cluster"
  value       = var.create_eks_cluster ? aws_vpc.eks_vpc[0].id : ""
}

output "eks_public_subnet_ids" {
  description = "Public subnet IDs for EKS cluster"
  value       = var.create_eks_cluster ? aws_subnet.eks_public[*].id : []
}

# GitHub Actions Deploy Policy Outputs
output "github_actions_eks_deploy_policy_arn" {
  description = "ARN of the GitHub Actions EKS deploy policy"
  value       = var.github_actions_user_name != "" ? aws_iam_policy.github_actions_eks_deploy[0].arn : ""
}

output "github_actions_user_name" {
  description = "Name of the IAM user for GitHub Actions deployment"
  value       = var.github_actions_user_name
}

