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

# CloudWatch Log Streams are created automatically by the application
# No need to pre-create them as pods will create dynamic streams based on hostname
# Keeping this commented for reference:
# resource "aws_cloudwatch_log_stream" "fraud_detection_application" {
#   count          = var.enable_cloudwatch_logs ? 1 : 0
#   name           = "application"
#   log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
# }

# ============================================================================
# CLOUDWATCH LOG METRIC FILTERS - Extract metrics from structured logs
# ============================================================================

# Fraud Detection Metrics
# Note: CloudWatch filter patterns use quoted strings for literal matching with special chars
resource "aws_cloudwatch_log_metric_filter" "fraud_detected" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "fraud-detected-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "\"FRAUD_DETECTED\""
  
  metric_transformation {
    name          = "FraudDetectedCount"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

# Fraud by Severity - HIGH
resource "aws_cloudwatch_log_metric_filter" "fraud_severity_high" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "fraud-severity-high-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "\"FRAUD_DETECTED\" \"severity=HIGH\""
  
  metric_transformation {
    name          = "FraudSeverityHigh"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

# Fraud by Severity - MEDIUM
resource "aws_cloudwatch_log_metric_filter" "fraud_severity_medium" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "fraud-severity-medium-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "\"FRAUD_DETECTED\" \"severity=MEDIUM\""
  
  metric_transformation {
    name          = "FraudSeverityMedium"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

# Fraud by Severity - LOW
resource "aws_cloudwatch_log_metric_filter" "fraud_severity_low" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "fraud-severity-low-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "\"FRAUD_DETECTED\" \"severity=LOW\""
  
  metric_transformation {
    name          = "FraudSeverityLow"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

# Transaction Processing Metrics
resource "aws_cloudwatch_log_metric_filter" "transaction_received" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "transaction-received-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "\"TRANSACTION_RECEIVED\""
  
  metric_transformation {
    name          = "TransactionsReceived"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

resource "aws_cloudwatch_log_metric_filter" "transaction_processed" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "transaction-processed-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "\"TRANSACTION_PROCESSED\""
  
  metric_transformation {
    name          = "TransactionsProcessed"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

resource "aws_cloudwatch_log_metric_filter" "transaction_cleared" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "transaction-cleared-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "\"TRANSACTION_CLEARED\""
  
  metric_transformation {
    name          = "TransactionsCleared"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

# Transaction Producer Metrics
resource "aws_cloudwatch_log_metric_filter" "transaction_sent" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "transaction-sent-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.transaction_producer_logs[0].name
  pattern        = "\"TRANSACTION_SENT\""
  
  metric_transformation {
    name          = "TransactionsSent"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

# Performance Metrics - High Latency
resource "aws_cloudwatch_log_metric_filter" "high_latency" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "high-latency-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "\"HIGH_LATENCY\""
  
  metric_transformation {
    name          = "HighLatencyCount"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

# Ring Buffer Metrics - High Utilization Warning
resource "aws_cloudwatch_log_metric_filter" "ring_buffer_high" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "ring-buffer-high-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "\"RING_BUFFER_HIGH_UTILIZATION\""
  
  metric_transformation {
    name          = "RingBufferHighUtilization"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

# Error Metrics
resource "aws_cloudwatch_log_metric_filter" "error_count" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "error-count-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "ERROR"
  
  metric_transformation {
    name          = "ErrorCount"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

resource "aws_cloudwatch_log_metric_filter" "processing_error" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "processing-error-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "\"PROCESSING_ERROR\""
  
  metric_transformation {
    name          = "ProcessingErrors"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

# Rule Violation Metrics
resource "aws_cloudwatch_log_metric_filter" "rule_large_amount" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "rule-large-amount-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "\"RULE_VIOLATION\" \"rule=LARGE_AMOUNT\""
  
  metric_transformation {
    name          = "RuleViolationLargeAmount"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

resource "aws_cloudwatch_log_metric_filter" "rule_suspicious_account" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "rule-suspicious-account-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "\"RULE_VIOLATION\" \"rule=SUSPICIOUS_ACCOUNT\""
  
  metric_transformation {
    name          = "RuleViolationSuspiciousAccount"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

resource "aws_cloudwatch_log_metric_filter" "rule_rapid_fire" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  name           = "rule-rapid-fire-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.fraud_detection_logs[0].name
  pattern        = "\"RULE_VIOLATION\" \"rule=RAPID_FIRE\""
  
  metric_transformation {
    name          = "RuleViolationRapidFire"
    namespace     = "FraudDetection/${var.environment}"
    value         = "1"
    default_value = "0"
    unit          = "Count"
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

# ============================================================================
# CLOUDWATCH ALARMS - Proactive alerting for fraud detection
# ============================================================================

# Alarms for SQS
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

# Alarms for Fraud Detection Metrics
resource "aws_cloudwatch_metric_alarm" "high_fraud_rate" {
  count               = var.enable_cloudwatch_logs ? 1 : 0
  alarm_name          = "fraud-detection-high-fraud-rate-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "3"
  metric_name         = "FraudDetectedCount"
  namespace           = "FraudDetection/${var.environment}"
  period              = "300"
  statistic           = "Sum"
  threshold           = var.fraud_alert_threshold
  alarm_description   = "Alert when fraud detection rate exceeds ${var.fraud_alert_threshold} per 5 minutes"
  treat_missing_data  = "notBreaching"
  
  tags = merge(var.tags, {
    Severity = "HIGH"
    Service  = "fraud-detection"
  })
}

resource "aws_cloudwatch_metric_alarm" "high_severity_fraud" {
  count               = var.enable_cloudwatch_logs ? 1 : 0
  alarm_name          = "fraud-detection-high-severity-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "FraudSeverityHigh"
  namespace           = "FraudDetection/${var.environment}"
  period              = "60"
  statistic           = "Sum"
  threshold           = "5"
  alarm_description   = "Alert when high severity fraud exceeds 5 per minute"
  treat_missing_data  = "notBreaching"
  
  tags = merge(var.tags, {
    Severity = "CRITICAL"
    Service  = "fraud-detection"
  })
}

resource "aws_cloudwatch_metric_alarm" "high_error_rate" {
  count               = var.enable_cloudwatch_logs ? 1 : 0
  alarm_name          = "fraud-detection-high-error-rate-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "ErrorCount"
  namespace           = "FraudDetection/${var.environment}"
  period              = "300"
  statistic           = "Sum"
  threshold           = var.error_alert_threshold
  alarm_description   = "Alert when error rate exceeds ${var.error_alert_threshold} per 5 minutes"
  treat_missing_data  = "notBreaching"
  
  tags = merge(var.tags, {
    Severity = "HIGH"
    Service  = "fraud-detection"
  })
}

resource "aws_cloudwatch_metric_alarm" "high_latency_alert" {
  count               = var.enable_cloudwatch_logs ? 1 : 0
  alarm_name          = "fraud-detection-high-latency-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "3"
  metric_name         = "HighLatencyCount"
  namespace           = "FraudDetection/${var.environment}"
  period              = "300"
  statistic           = "Sum"
  threshold           = "10"
  alarm_description   = "Alert when high latency events exceed 10 per 5 minutes"
  treat_missing_data  = "notBreaching"
  
  tags = merge(var.tags, {
    Severity = "MEDIUM"
    Service  = "fraud-detection"
  })
}

resource "aws_cloudwatch_metric_alarm" "ring_buffer_pressure" {
  count               = var.enable_cloudwatch_logs ? 1 : 0
  alarm_name          = "fraud-detection-ring-buffer-pressure-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "RingBufferHighUtilization"
  namespace           = "FraudDetection/${var.environment}"
  period              = "60"
  statistic           = "Sum"
  threshold           = "5"
  alarm_description   = "Alert when ring buffer high utilization warnings exceed 5 per minute"
  treat_missing_data  = "notBreaching"
  
  tags = merge(var.tags, {
    Severity = "HIGH"
    Service  = "fraud-detection"
  })
}

# ============================================================================
# JVM HEALTH METRICS ALARMS - Monitor JVM performance and health
# ============================================================================

# JVM Heap Memory Usage Alarm - Alert when heap usage exceeds 85%
resource "aws_cloudwatch_metric_alarm" "jvm_heap_memory_high" {
  count               = var.enable_cloudwatch_logs ? 1 : 0
  alarm_name          = "fraud-detection-jvm-heap-memory-high-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  threshold           = "85"  # 85% of max heap
  alarm_description   = "Alert when JVM heap memory usage exceeds 85%"
  treat_missing_data  = "notBreaching"
  
  # Use metric math to calculate percentage: (used / max) * 100
  metric_query {
    id          = "e1"
    expression  = "(m1 / m2) * 100"
    label       = "Heap Memory Usage (%)"
    return_data = true
  }
  
  metric_query {
    id = "m1"
    metric {
      metric_name = "jvm.memory.used"
      namespace   = "FraudDetection/${var.environment}"
      period      = "300"
      stat        = "Average"
      dimensions = {
        area = "heap"
      }
    }
  }
  
  metric_query {
    id = "m2"
    metric {
      metric_name = "jvm.memory.max"
      namespace   = "FraudDetection/${var.environment}"
      period      = "300"
      stat        = "Average"
      dimensions = {
        area = "heap"
      }
    }
  }
  
  tags = merge(var.tags, {
    Severity = "HIGH"
    Service  = "fraud-detection"
    Metric   = "JVM"
  })
}

# JVM GC Pause Time Alarm - Alert when GC pauses exceed 1 second
resource "aws_cloudwatch_metric_alarm" "jvm_gc_pause_high" {
  count               = var.enable_cloudwatch_logs ? 1 : 0
  alarm_name          = "fraud-detection-jvm-gc-pause-high-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "jvm.gc.pause"
  namespace           = "FraudDetection/${var.environment}"
  period              = "300"
  statistic           = "Average"
  threshold           = "1000"  # 1 second in milliseconds
  alarm_description   = "Alert when JVM GC pause time exceeds 1 second"
  treat_missing_data  = "notBreaching"
  
  tags = merge(var.tags, {
    Severity = "MEDIUM"
    Service  = "fraud-detection"
    Metric   = "JVM"
  })
}

# JVM Thread Count Alarm - Alert when thread count exceeds 500
resource "aws_cloudwatch_metric_alarm" "jvm_thread_count_high" {
  count               = var.enable_cloudwatch_logs ? 1 : 0
  alarm_name          = "fraud-detection-jvm-thread-count-high-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "jvm.threads.live"
  namespace           = "FraudDetection/${var.environment}"
  period              = "300"
  statistic           = "Average"
  threshold           = "500"
  alarm_description   = "Alert when JVM thread count exceeds 500"
  treat_missing_data  = "notBreaching"
  
  tags = merge(var.tags, {
    Severity = "MEDIUM"
    Service  = "fraud-detection"
    Metric   = "JVM"
  })
}

# JVM CPU Usage Alarm - Alert when process CPU usage exceeds 80%
resource "aws_cloudwatch_metric_alarm" "jvm_cpu_usage_high" {
  count               = var.enable_cloudwatch_logs ? 1 : 0
  alarm_name          = "fraud-detection-jvm-cpu-usage-high-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "process.cpu.usage"
  namespace           = "FraudDetection/${var.environment}"
  period              = "300"
  statistic           = "Average"
  threshold           = "0.80"  # 80% CPU usage
  alarm_description   = "Alert when JVM process CPU usage exceeds 80%"
  treat_missing_data  = "notBreaching"
  
  tags = merge(var.tags, {
    Severity = "HIGH"
    Service  = "fraud-detection"
    Metric   = "JVM"
  })
}

# ============================================================================
# CLOUDWATCH DASHBOARD - Operational visibility
# ============================================================================

resource "aws_cloudwatch_dashboard" "fraud_detection" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  dashboard_name = "FraudDetection-${var.environment}"
  
  dashboard_body = jsonencode({
    widgets = [
      # Row 1: Key Metrics Overview
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 6
        height = 6
        properties = {
          title  = "Fraud Detected"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "FraudDetectedCount", { stat = "Sum", period = 60, color = "#d62728" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 6
        y      = 0
        width  = 6
        height = 6
        properties = {
          title  = "Fraud by Severity"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "FraudSeverityHigh", { stat = "Sum", period = 60, color = "#d62728", label = "High" }],
            [".", "FraudSeverityMedium", { stat = "Sum", period = 60, color = "#ff7f0e", label = "Medium" }],
            [".", "FraudSeverityLow", { stat = "Sum", period = 60, color = "#2ca02c", label = "Low" }]
          ]
          view = "timeSeries"
          stacked = true
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 6
        height = 6
        properties = {
          title  = "Transactions Flow"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "TransactionsSent", { stat = "Sum", period = 60, color = "#1f77b4", label = "Sent" }],
            [".", "TransactionsReceived", { stat = "Sum", period = 60, color = "#17becf", label = "Received" }],
            [".", "TransactionsProcessed", { stat = "Sum", period = 60, color = "#2ca02c", label = "Processed" }],
            [".", "TransactionsCleared", { stat = "Sum", period = 60, color = "#98df8a", label = "Cleared" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 18
        y      = 0
        width  = 6
        height = 6
        properties = {
          title  = "Error Rate"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "ErrorCount", { stat = "Sum", period = 60, color = "#d62728" }],
            [".", "ProcessingErrors", { stat = "Sum", period = 60, color = "#ff7f0e" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      
      # Row 2: Rule Violations & Performance
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 8
        height = 6
        properties = {
          title  = "Rule Violations by Type"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "RuleViolationLargeAmount", { stat = "Sum", period = 300, label = "Large Amount" }],
            [".", "RuleViolationSuspiciousAccount", { stat = "Sum", period = 300, label = "Suspicious Account" }],
            [".", "RuleViolationRapidFire", { stat = "Sum", period = 300, label = "Rapid Fire" }]
          ]
          view = "bar"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 8
        y      = 6
        width  = 8
        height = 6
        properties = {
          title  = "Performance Indicators"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "HighLatencyCount", { stat = "Sum", period = 60, color = "#ff7f0e", label = "High Latency" }],
            [".", "RingBufferHighUtilization", { stat = "Sum", period = 60, color = "#d62728", label = "Ring Buffer Pressure" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 16
        y      = 6
        width  = 8
        height = 6
        properties = {
          title  = "SQS Queue Health"
          region = var.aws_region
          metrics = [
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", local.queue_name, { stat = "Average", period = 60, label = "Messages Visible" }],
            [".", "ApproximateAgeOfOldestMessage", "QueueName", local.queue_name, { stat = "Maximum", period = 60, label = "Oldest Message Age (s)", yAxis = "right" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      
      # Row 3: Summary Stats
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 4
        height = 4
        properties = {
          title  = "Total Fraud (24h)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "FraudDetectedCount", { stat = "Sum", period = 86400 }]
          ]
          view = "singleValue"
        }
      },
      {
        type   = "metric"
        x      = 4
        y      = 12
        width  = 4
        height = 4
        properties = {
          title  = "Total Transactions (24h)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "TransactionsProcessed", { stat = "Sum", period = 86400 }]
          ]
          view = "singleValue"
        }
      },
      {
        type   = "metric"
        x      = 8
        y      = 12
        width  = 4
        height = 4
        properties = {
          title  = "Fraud Rate (%)"
          region = var.aws_region
          metrics = [
            [{ expression = "100 * m1 / m2", label = "Fraud Rate", id = "e1" }],
            ["FraudDetection/${var.environment}", "FraudDetectedCount", { stat = "Sum", period = 86400, id = "m1", visible = false }],
            [".", "TransactionsProcessed", { stat = "Sum", period = 86400, id = "m2", visible = false }]
          ]
          view = "singleValue"
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 12
        width  = 4
        height = 4
        properties = {
          title  = "Errors (24h)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "ErrorCount", { stat = "Sum", period = 86400, color = "#d62728" }]
          ]
          view = "singleValue"
        }
      },
      {
        type   = "metric"
        x      = 16
        y      = 12
        width  = 8
        height = 4
        properties = {
          title  = "DLQ Messages"
          region = var.aws_region
          metrics = [
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", local.dlq_name, { stat = "Sum", period = 300, color = "#d62728" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      
      # Row 4: JVM Health Metrics
      {
        type   = "metric"
        x      = 0
        y      = 16
        width  = 8
        height = 6
        properties = {
          title  = "JVM Heap Memory Usage"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "jvm.memory.used", { stat = "Average", period = 60, color = "#1f77b4", label = "Used (bytes)" }],
            [".", "jvm.memory.max", { stat = "Average", period = 60, color = "#ff7f0e", label = "Max (bytes)", yAxis = "right" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 8
        y      = 16
        width  = 8
        height = 6
        properties = {
          title  = "JVM Garbage Collection"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "jvm.gc.pause", { stat = "Average", period = 60, color = "#d62728", label = "GC Pause (ms)" }],
            [".", "jvm.gc.pause", { stat = "Maximum", period = 60, color = "#ff7f0e", label = "Max GC Pause (ms)" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 16
        y      = 16
        width  = 8
        height = 6
        properties = {
          title  = "JVM Threads"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "jvm.threads.live", { stat = "Average", period = 60, color = "#2ca02c", label = "Live Threads" }],
            [".", "jvm.threads.peak", { stat = "Maximum", period = 60, color = "#ff7f0e", label = "Peak Threads" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 22
        width  = 6
        height = 6
        properties = {
          title  = "JVM Process CPU Usage"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "process.cpu.usage", { stat = "Average", period = 60, color = "#9467bd", label = "CPU Usage" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 6
        y      = 22
        width  = 6
        height = 6
        properties = {
          title  = "JVM Classes Loaded"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "jvm.classes.loaded", { stat = "Average", period = 60, color = "#9467bd", label = "Loaded Classes" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 22
        width  = 6
        height = 6
        properties = {
          title  = "JVM Process Uptime"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "process.uptime", { stat = "Average", period = 60, color = "#17becf", label = "Uptime (seconds)" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 18
        y      = 22
        width  = 6
        height = 6
        properties = {
          title  = "JVM Non-Heap Memory"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "jvm.memory.used", { stat = "Average", period = 60, color = "#8c564b", label = "Non-Heap Used (bytes)" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      }
    ]
  })
}

