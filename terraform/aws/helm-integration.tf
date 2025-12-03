# This file contains helpful outputs and documentation for Helm integration

# Output the exact service account annotations needed for Helm values files
output "helm_serviceaccount_annotations_fraud_detection" {
  description = "ServiceAccount annotations for fraud-detection Helm chart"
  value = {
    "eks.amazonaws.com/role-arn" = aws_iam_role.fraud_detection_role.arn
  }
}

output "helm_serviceaccount_annotations_transaction_producer" {
  description = "ServiceAccount annotations for transaction-producer Helm chart"
  value = {
    "eks.amazonaws.com/role-arn" = aws_iam_role.transaction_producer_role.arn
  }
}

# Output the complete values snippet for easy copy-paste
output "helm_values_snippet_fraud_detection" {
  description = "Complete Helm values snippet for fraud-detection service"
  value = <<-EOT
    serviceAccount:
      create: true
      annotations:
        eks.amazonaws.com/role-arn: ${aws_iam_role.fraud_detection_role.arn}
  EOT
}

output "helm_values_snippet_transaction_producer" {
  description = "Complete Helm values snippet for transaction-producer service"
  value = <<-EOT
    serviceAccount:
      create: true
      annotations:
        eks.amazonaws.com/role-arn: ${aws_iam_role.transaction_producer_role.arn}
  EOT
}

# Output CloudWatch log group names for application configuration
output "cloudwatch_log_groups" {
  description = "CloudWatch log group names for application configuration"
  value = {
    fraud_detection  = var.enable_cloudwatch_logs ? aws_cloudwatch_log_group.fraud_detection_logs[0].name : ""
    transaction_producer = var.enable_cloudwatch_logs ? aws_cloudwatch_log_group.transaction_producer_logs[0].name : ""
  }
}

