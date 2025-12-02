# CloudWatch Log Groups for Distributed Logging
resource "aws_cloudwatch_log_group" "fraud_detection_logs" {
  count             = var.enable_cloudwatch_logs ? 1 : 0
  name              = "/aws/fraud-detection/${var.environment}/fraud-detection-service"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.cloudwatch_kms_key_id
  
  tags = merge(
    var.tags,
    {
      Name        = "fraud-detection-service-logs"
      Environment = var.environment
      Service     = "fraud-detection-service"
    }
  )
}

resource "aws_cloudwatch_log_group" "transaction_producer_logs" {
  count             = var.enable_cloudwatch_logs ? 1 : 0
  name              = "/aws/fraud-detection/${var.environment}/transaction-producer"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.cloudwatch_kms_key_id
  
  tags = merge(
    var.tags,
    {
      Name        = "transaction-producer-logs"
      Environment = var.environment
      Service     = "transaction-producer"
    }
  )
}

# CloudWatch Log Streams for different log types
resource "aws_cloudwatch_log_stream" "fraud_detection_application" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "application"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
}

resource "aws_cloudwatch_log_stream" "fraud_detection_errors" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "errors"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
}

resource "aws_cloudwatch_log_stream" "transaction_producer_application" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "application"
  log_group_name = aws_cloudwatch_log_group.transaction_producer_logs[0].name
}

# CloudWatch Log Metric Filters for fraud detection
resource "aws_cloudwatch_log_metric_filter" "fraud_detected" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "fraud-detected-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "[time, request_id, correlation_id, level=ERROR*, logger, message=FRAUD_DETECTED*]"
  
  metric_transformation {
    name      = "FraudDetectedCount"
    namespace = "FraudDetection"
    value     = "1"
    default_value = 0
    unit      = "Count"
    
    dimensions = {
      Environment = var.environment
      Service     = "fraud-detection-service"
    }
  }
}

resource "aws_cloudwatch_log_metric_filter" "high_latency" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "high-latency-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "[time, request_id, correlation_id, level, logger, message=\"High latency detected*\"]"
  
  metric_transformation {
    name      = "HighLatencyCount"
    namespace = "FraudDetection"
    value     = "1"
    default_value = 0
    unit      = "Count"
    
    dimensions = {
      Environment = var.environment
      Service     = "fraud-detection-service"
    }
  }
}

# CloudWatch Insights Queries for common investigations
resource "aws_cloudwatch_query_definition" "fraud_alerts_by_severity" {
  count = var.enable_cloudwatch_logs ? 1 : 0
  name  = "Fraud Alerts by Severity - ${var.environment}"
  
  log_group_names = [
    aws_cloudwatch_log_group.fraud_detection_logs[0].name
  ]
  
  query_string = <<-QUERY
    fields @timestamp, severity, transactionId, accountId, message
    | filter message like /FRAUD_DETECTED/
    | stats count() by severity
    | sort count desc
  QUERY
}

resource "aws_cloudwatch_query_definition" "error_analysis" {
  count = var.enable_cloudwatch_logs ? 1 : 0
  name  = "Error Analysis - ${var.environment}"
  
  log_group_names = [
    aws_cloudwatch_log_group.fraud_detection_logs[0].name
  ]
  
  query_string = <<-QUERY
    fields @timestamp, @message, correlationId, logger
    | filter level = "ERROR"
    | sort @timestamp desc
    | limit 100
  QUERY
}

resource "aws_cloudwatch_query_definition" "transaction_trace" {
  count = var.enable_cloudwatch_logs ? 1 : 0
  name  = "Transaction Trace by Correlation ID - ${var.environment}"
  
  log_group_names = [
    aws_cloudwatch_log_group.fraud_detection_logs[0].name,
    aws_cloudwatch_log_group.transaction_producer_logs[0].name
  ]
  
  query_string = <<-QUERY
    fields @timestamp, @message, serviceName, correlationId, transactionId
    | filter correlationId like /.+/
    | sort @timestamp asc
  QUERY
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

