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

