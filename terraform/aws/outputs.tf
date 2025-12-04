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

output "eks_pod_identity_agent_addon_version" {
  description = "Version of EKS Pod Identity Agent addon"
  value       = var.create_eks_cluster ? aws_eks_addon.pod_identity_agent[0].addon_version : ""
}

output "eks_vpc_cni_addon_version" {
  description = "Version of VPC CNI addon"
  value       = var.create_eks_cluster ? aws_eks_addon.vpc_cni[0].addon_version : ""
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

# Container Insights Outputs
output "container_insights_enabled" {
  description = "Whether Container Insights is enabled"
  value       = var.enable_container_insights
}

output "container_insights_log_groups" {
  description = "Container Insights CloudWatch log group names"
  value = var.create_eks_cluster && var.enable_container_insights ? {
    application = aws_cloudwatch_log_group.container_insights_application[0].name
    dataplane   = aws_cloudwatch_log_group.container_insights_dataplane[0].name
    host        = aws_cloudwatch_log_group.container_insights_host[0].name
    performance = aws_cloudwatch_log_group.container_insights_performance[0].name
  } : {}
}

output "cloudwatch_observability_addon_version" {
  description = "Version of CloudWatch Observability addon"
  value       = var.create_eks_cluster && var.enable_container_insights ? aws_eks_addon.cloudwatch_observability[0].addon_version : ""
}

output "cloudwatch_dashboard_url" {
  description = "URL to the CloudWatch dashboard"
  value       = var.enable_cloudwatch_logs ? "https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=FraudDetection-${var.environment}" : ""
}

output "container_insights_dashboard_url" {
  description = "URL to the Container Insights dashboard"
  value       = var.create_eks_cluster && var.enable_container_insights ? "https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#container-insights:infrastructure/map/EKS:Cluster/${var.eks_cluster_name}-${var.environment}" : ""
}

