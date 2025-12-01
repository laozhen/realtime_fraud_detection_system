# CloudWatch Log Groups
resource "aws_cloudwatch_log_group" "fraud_detection_logs" {
  count             = var.enable_cloudwatch_logs ? 1 : 0
  name              = "/aws/fraud-detection/${var.environment}/fraud-detection-service"
  retention_in_days = var.log_retention_days
  
  tags = merge(
    var.tags,
    {
      Name = "fraud-detection-service-logs"
    }
  )
}

resource "aws_cloudwatch_log_group" "transaction_producer_logs" {
  count             = var.enable_cloudwatch_logs ? 1 : 0
  name              = "/aws/fraud-detection/${var.environment}/transaction-producer"
  retention_in_days = var.log_retention_days
  
  tags = merge(
    var.tags,
    {
      Name = "transaction-producer-logs"
    }
  )
}

# CloudWatch Alarms for SQS
resource "aws_cloudwatch_metric_alarm" "sqs_old_messages" {
  alarm_name          = "${local.queue_name}-old-messages"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "ApproximateAgeOfOldestMessage"
  namespace           = "AWS/SQS"
  period              = "300"
  statistic           = "Maximum"
  threshold           = "300"  # 5 minutes
  alarm_description   = "Alert when messages are stuck in queue"
  
  dimensions = {
    QueueName = aws_sqs_queue.fraud_detection_queue.name
  }
  
  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "sqs_dlq_messages" {
  alarm_name          = "${local.dlq_name}-messages"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = "60"
  statistic           = "Average"
  threshold           = "0"
  alarm_description   = "Alert when messages appear in DLQ"
  
  dimensions = {
    QueueName = aws_sqs_queue.fraud_detection_dlq.name
  }
  
  tags = var.tags
}

