# ============================================================================
# LOCALS
# ============================================================================

locals {
  fraud_detection_application             = "fraud-detection-service"
  transaction_producer_application        = "transaction-producer"
  fraud_detection_error_type              = "TRANSACTION_PROCESSING"
  fraud_detection_latency_operation       = "transaction-processing"
  transaction_producer_latency_operation  = "sqs-publish"
  transaction_producer_error_type_primary = "PUBLISH_ERROR"
  transaction_producer_error_type_sqs     = "SQS_PUBLISH_ERROR"
}

# ============================================================================
# CONTAINER INSIGHTS LOG GROUPS
# ============================================================================

# Container Insights - Application logs
resource "aws_cloudwatch_log_group" "container_insights_application" {
  count             = var.create_eks_cluster && var.enable_container_insights ? 1 : 0
  name              = "/aws/containerinsights/${var.eks_cluster_name}-${var.environment}/application"
  retention_in_days = var.container_insights_log_retention_days
  kms_key_id        = var.cloudwatch_kms_key_id
  
  tags = merge(
    var.tags,
    {
      Name        = "container-insights-application"
      Environment = var.environment
      Service     = "container-insights"
    }
  )
}

# Container Insights - Dataplane logs
resource "aws_cloudwatch_log_group" "container_insights_dataplane" {
  count             = var.create_eks_cluster && var.enable_container_insights ? 1 : 0
  name              = "/aws/containerinsights/${var.eks_cluster_name}-${var.environment}/dataplane"
  retention_in_days = var.container_insights_log_retention_days
  kms_key_id        = var.cloudwatch_kms_key_id
  
  tags = merge(
    var.tags,
    {
      Name        = "container-insights-dataplane"
      Environment = var.environment
      Service     = "container-insights"
    }
  )
}

# Container Insights - Host logs
resource "aws_cloudwatch_log_group" "container_insights_host" {
  count             = var.create_eks_cluster && var.enable_container_insights ? 1 : 0
  name              = "/aws/containerinsights/${var.eks_cluster_name}-${var.environment}/host"
  retention_in_days = var.container_insights_log_retention_days
  kms_key_id        = var.cloudwatch_kms_key_id
  
  tags = merge(
    var.tags,
    {
      Name        = "container-insights-host"
      Environment = var.environment
      Service     = "container-insights"
    }
  )
}

# Container Insights - Performance logs
resource "aws_cloudwatch_log_group" "container_insights_performance" {
  count             = var.create_eks_cluster && var.enable_container_insights ? 1 : 0
  name              = "/aws/containerinsights/${var.eks_cluster_name}-${var.environment}/performance"
  retention_in_days = var.container_insights_log_retention_days
  kms_key_id        = var.cloudwatch_kms_key_id
  
  tags = merge(
    var.tags,
    {
      Name        = "container-insights-performance"
      Environment = var.environment
      Service     = "container-insights"
    }
  )
}

# ============================================================================
# APPLICATION LOG GROUPS
# ============================================================================

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
# 
# NOTE: Application metrics are now emitted directly to CloudWatch via Micrometer.
# We keep the fraud_detected log-based counter for redundancy so alerts still fire
# even if the metrics publisher is unavailable.
# 
# All operational metrics now live in the CloudWatch namespace
# FraudDetection/${var.environment} (see application configuration for details):
# - Transactions: transactions_received_total.count, transactions_processed_total.count, etc.
# - Errors: processing_errors_total.count
# - Latency: transaction_processing_duration_ms, sqs_publish_latency_seconds
# - Rule Violations: rule_violations_total.count
# - Ring Buffer: ring_buffer_utilization_percent, ring_buffer_high_utilization_total.count
# ============================================================================

# Fraud Detection Metrics - KEEP for critical alerting redundancy
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
# CLOUDWATCH DASHBOARD - Operational visibility
# ============================================================================

resource "aws_cloudwatch_dashboard" "fraud_detection" {
  count          = var.enable_cloudwatch_logs ? 1 : 0
  dashboard_name = "FraudDetection-${var.environment}"
  
  dashboard_body = jsonencode({
    widgets = [
      # Row 1: Pod CPU Utilization & Pod Count (Container Insights)
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 8
        height = 6
        properties = {
          title  = "Pod CPU Utilization - All Pods by Name"
          region = var.aws_region
          metrics = [
            [{ expression = "SEARCH('{ContainerInsights,ClusterName,Namespace,PodName} MetricName=\"pod_cpu_utilization\" ClusterName=\"${var.eks_cluster_name}-${var.environment}\" Namespace=\"fraud-detection\"', 'Average', 60)", id = "e1" }]
          ]
          view = "timeSeries"
          stacked = false
          yAxis = {
            left = {
              label = "CPU Utilization %"
              min = 0
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 8
        y      = 0
        width  = 8
        height = 6
        properties = {
          title  = "Pod CPU Utilization - Average by Namespace"
          region = var.aws_region
          metrics = [
            ["ContainerInsights", "pod_cpu_utilization", "ClusterName", "${var.eks_cluster_name}-${var.environment}", "Namespace", "fraud-detection", { stat = "Average", period = 60, color = "#1f77b4", label = "Avg CPU %" }],
            ["ContainerInsights", "pod_cpu_utilization", "ClusterName", "${var.eks_cluster_name}-${var.environment}", "Namespace", "fraud-detection", { stat = "Maximum", period = 60, color = "#d62728", label = "Max CPU %" }]
          ]
          view = "timeSeries"
          stacked = false
          yAxis = {
            left = {
              label = "CPU Utilization %"
              min = 0
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 16
        y      = 0
        width  = 8
        height = 6
        properties = {
          title  = "Pod Count by Service"
          region = var.aws_region
          metrics = [
            ["ContainerInsights", "service_number_of_running_pods", "ClusterName", "${var.eks_cluster_name}-${var.environment}", "Namespace", "fraud-detection", "Service", "fraud-detection", { stat = "Average", period = 60, color = "#2ca02c", label = "Fraud Detection" }],
            ["ContainerInsights", "service_number_of_running_pods", "ClusterName", "${var.eks_cluster_name}-${var.environment}", "Namespace", "fraud-detection", "Service", "transaction-producer", { stat = "Average", period = 60, color = "#ff7f0e", label = "Transaction Producer" }]
          ]
          view = "timeSeries"
          stacked = false
          yAxis = {
            left = {
              label = "Pod Count"
              min = 0
            }
          }
        }
      },
      
      # Row 2: Pod Memory & Count Details
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 8
        height = 6
        properties = {
          title  = "Pod Memory Utilization - All Pods"
          region = var.aws_region
          metrics = [
            [{ expression = "SEARCH('{ContainerInsights,ClusterName,Namespace,PodName} MetricName=\"pod_memory_utilization\" ClusterName=\"${var.eks_cluster_name}-${var.environment}\" Namespace=\"fraud-detection\"', 'Average', 60)", id = "e1" }]
          ]
          view = "timeSeries"
          stacked = false
          yAxis = {
            left = {
              label = "Memory Utilization %"
              min = 0
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 8
        y      = 6
        width  = 8
        height = 6
        properties = {
          title  = "Pod Network (Bytes/sec)"
          region = var.aws_region
          metrics = [
            ["ContainerInsights", "pod_network_rx_bytes", "ClusterName", "${var.eks_cluster_name}-${var.environment}", "Namespace", "fraud-detection", { stat = "Average", period = 60, color = "#1f77b4", label = "RX Bytes" }],
            ["ContainerInsights", "pod_network_tx_bytes", "ClusterName", "${var.eks_cluster_name}-${var.environment}", "Namespace", "fraud-detection", { stat = "Average", period = 60, color = "#2ca02c", label = "TX Bytes" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 16
        y      = 6
        width  = 4
        height = 6
        properties = {
          title  = "Total Running Pods"
          region = var.aws_region
          metrics = [
            ["ContainerInsights", "namespace_number_of_running_pods", "ClusterName", "${var.eks_cluster_name}-${var.environment}", "Namespace", "fraud-detection", { stat = "Average", period = 60 }]
          ]
          view = "singleValue"
        }
      },
      
      # Row 3: Business Metrics Overview
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 6
        height = 6
        properties = {
          title  = "Fraud by Severity (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "fraud_detected_total.count", "application", local.fraud_detection_application, "severity", "HIGH", { stat = "Sum", period = 60, color = "#d62728", label = "High" }],
            ["FraudDetection/${var.environment}", "fraud_detected_total.count", "application", local.fraud_detection_application, "severity", "MEDIUM", { stat = "Sum", period = 60, color = "#ff7f0e", label = "Medium" }],
            ["FraudDetection/${var.environment}", "fraud_detected_total.count", "application", local.fraud_detection_application, "severity", "LOW", { stat = "Sum", period = 60, color = "#2ca02c", label = "Low" }]
          ]
          view = "timeSeries"
          stacked = true
        }
      },
      {
        type   = "metric"
        x      = 6
        y      = 12
        width  = 6
        height = 6
        properties = {
          title  = "Transactions Flow (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "transactions_sent_total.count", "application", local.transaction_producer_application, { stat = "Sum", period = 60, color = "#1f77b4", label = "Sent" }],
            ["FraudDetection/${var.environment}", "transactions_received_total.count", "application", local.fraud_detection_application, { stat = "Sum", period = 60, color = "#17becf", label = "Received" }],
            ["FraudDetection/${var.environment}", "transactions_processed_total.count", "application", local.fraud_detection_application, { stat = "Sum", period = 60, color = "#2ca02c", label = "Processed" }],
            ["FraudDetection/${var.environment}", "transactions_cleared_total.count", "application", local.fraud_detection_application, { stat = "Sum", period = 60, color = "#98df8a", label = "Cleared" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 18
        y      = 12
        width  = 6
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
      
      # Row 4: Performance & Summary Stats
      {
        type   = "metric"
        x      = 0
        y      = 18
        width  = 6
        height = 6
        properties = {
          title  = "Performance Indicators (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "high_latency_events_total.count", "application", local.fraud_detection_application, "operation", local.fraud_detection_latency_operation, { stat = "Sum", period = 60, color = "#ff7f0e", label = "High Latency Events" }],
            ["FraudDetection/${var.environment}", "ring_buffer_utilization_percent.value", "application", local.fraud_detection_application, { stat = "Average", period = 60, color = "#d62728", label = "Ring Buffer %" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 6
        y      = 18
        width  = 3
        height = 6
        properties = {
          title  = "Total Fraud (24h)"
          region = var.aws_region
          metrics = [
            [{ expression = "m_high_24h + m_medium_24h + m_low_24h", label = "Total Fraud", id = "e_fraud_24h" }],
            ["FraudDetection/${var.environment}", "fraud_detected_total.count", "application", local.fraud_detection_application, "severity", "HIGH", { stat = "Sum", period = 86400, id = "m_high_24h", visible = false }],
            ["FraudDetection/${var.environment}", "fraud_detected_total.count", "application", local.fraud_detection_application, "severity", "MEDIUM", { stat = "Sum", period = 86400, id = "m_medium_24h", visible = false }],
            ["FraudDetection/${var.environment}", "fraud_detected_total.count", "application", local.fraud_detection_application, "severity", "LOW", { stat = "Sum", period = 86400, id = "m_low_24h", visible = false }]
          ]
          view = "singleValue"
        }
      },
      {
        type   = "metric"
        x      = 9
        y      = 18
        width  = 3
        height = 6
        properties = {
          title  = "Total Transactions (24h)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "transactions_processed_total.count", "application", local.fraud_detection_application, { stat = "Sum", period = 86400 }]
          ]
          view = "singleValue"
        }
      },
      {
        type   = "metric"
        x      = 18
        y      = 18
        width  = 6
        height = 6
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
      
      # Row 5: Transaction Producer Metrics
      {
        type   = "metric"
        x      = 0
        y      = 24
        width  = 8
        height = 6
        properties = {
          title  = "Transaction Producer - Error Rate (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "processing_errors_total.count", "application", local.transaction_producer_application, "error_type", local.transaction_producer_error_type_primary, { stat = "Sum", period = 60, color = "#d62728", label = "Publish Errors" }],
            ["FraudDetection/${var.environment}", "processing_errors_total.count", "application", local.transaction_producer_application, "error_type", local.transaction_producer_error_type_sqs, { stat = "Sum", period = 60, color = "#ff7f0e", label = "SQS Publish Errors" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 8
        y      = 24
        width  = 8
        height = 6
        properties = {
          title  = "SQS Publish Latency (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "sqs_publish_latency_seconds", "application", local.transaction_producer_application, "operation", local.transaction_producer_latency_operation, { stat = "Average", period = 60, color = "#1f77b4", label = "Avg Latency (s)" }],
            ["FraudDetection/${var.environment}", "sqs_publish_latency_seconds", "application", local.transaction_producer_application, "operation", local.transaction_producer_latency_operation, { stat = "p50", period = 60, color = "#2ca02c", label = "P50" }],
            ["FraudDetection/${var.environment}", "sqs_publish_latency_seconds", "application", local.transaction_producer_application, "operation", local.transaction_producer_latency_operation, { stat = "p90", period = 60, color = "#ff7f0e", label = "P90" }],
            ["FraudDetection/${var.environment}", "sqs_publish_latency_seconds", "application", local.transaction_producer_application, "operation", local.transaction_producer_latency_operation, { stat = "p99", period = 60, color = "#d62728", label = "P99" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 16
        y      = 24
        width  = 8
        height = 6
        properties = {
          title  = "High Latency Events (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "high_latency_events_total.count", "application", local.transaction_producer_application, "operation", local.transaction_producer_latency_operation, { stat = "Sum", period = 60, color = "#ff7f0e", label = "SQS High Latency" }],
            ["FraudDetection/${var.environment}", "high_latency_events_total.count", "application", local.fraud_detection_application, "operation", local.fraud_detection_latency_operation, { stat = "Sum", period = 60, color = "#d62728", label = "Processing High Latency" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 30
        width  = 12
        height = 6
        properties = {
          title  = "Transaction Publish Duration (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "transaction_publish_duration_seconds", "application", local.transaction_producer_application, { stat = "Average", period = 60, color = "#2ca02c", label = "Avg Duration (s)" }],
            ["FraudDetection/${var.environment}", "transaction_publish_duration_seconds", "application", local.transaction_producer_application, { stat = "p50", period = 60, color = "#1f77b4", label = "P50" }],
            ["FraudDetection/${var.environment}", "transaction_publish_duration_seconds", "application", local.transaction_producer_application, { stat = "p90", period = 60, color = "#ff7f0e", label = "P90" }],
            ["FraudDetection/${var.environment}", "transaction_publish_duration_seconds", "application", local.transaction_producer_application, { stat = "p99", period = 60, color = "#d62728", label = "P99" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 30
        width  = 12
        height = 6
        properties = {
          title  = "Transaction Production Rate (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "transactions_sent_total.count", "application", local.transaction_producer_application, { stat = "Sum", period = 60, color = "#1f77b4", label = "Transactions/min" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      
      # Row 6: JVM Health Metrics
      {
        type   = "metric"
        x      = 0
        y      = 36
        width  = 8
        height = 6
        properties = {
          title  = "JVM Heap Memory Usage (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "jvm_memory_used_bytes", "application", local.fraud_detection_application, "area", "heap", { stat = "Average", period = 60, color = "#1f77b4", label = "Used (bytes)" }],
            ["FraudDetection/${var.environment}", "jvm_memory_max_bytes", "application", local.fraud_detection_application, "area", "heap", { stat = "Average", period = 60, color = "#ff7f0e", label = "Max (bytes)" }]
          ]
          view = "timeSeries"
          stacked = false
          yAxis = {
            left = {
              label = "Bytes"
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 8
        y      = 36
        width  = 8
        height = 6
        properties = {
          title  = "JVM Garbage Collection (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "jvm_gc_pause_seconds", "application", local.fraud_detection_application, { stat = "Average", period = 60, color = "#d62728", label = "Avg GC Pause" }],
            ["FraudDetection/${var.environment}", "jvm_gc_pause_seconds", "application", local.fraud_detection_application, { stat = "p99", period = 60, color = "#ff7f0e", label = "P99 GC Pause" }]
          ]
          view = "timeSeries"
          stacked = false
          yAxis = {
            left = {
              label = "Seconds"
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 16
        y      = 36
        width  = 8
        height = 6
        properties = {
          title  = "JVM Threads (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "jvm_threads_live_threads", "application", local.fraud_detection_application, { stat = "Average", period = 60, color = "#2ca02c", label = "Live Threads" }],
            ["FraudDetection/${var.environment}", "jvm_threads_peak_threads", "application", local.fraud_detection_application, { stat = "Maximum", period = 60, color = "#ff7f0e", label = "Peak Threads" }]
          ]
          view = "timeSeries"
          stacked = false
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 42
        width  = 8
        height = 6
        properties = {
          title  = "JVM Process CPU Usage (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "process_cpu_usage", "application", local.fraud_detection_application, { stat = "Average", period = 60, color = "#9467bd", label = "CPU Usage" }]
          ]
          view = "timeSeries"
          stacked = false
          yAxis = {
            left = {
              label = "Percentage (0-1)"
            }
          }
        }
      },

      {
        type   = "metric"
        x      = 8
        y      = 42
        width  = 8
        height = 6
        properties = {
          title  = "Transaction Processing Duration (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "transaction_processing_duration_ms", "application", local.fraud_detection_application, { stat = "Average", period = 60, color = "#1f77b4", label = "Avg" }],
            ["FraudDetection/${var.environment}", "transaction_processing_duration_ms", "application", local.fraud_detection_application, { stat = "p50", period = 60, color = "#2ca02c", label = "P50" }],
            ["FraudDetection/${var.environment}", "transaction_processing_duration_ms", "application", local.fraud_detection_application, { stat = "p90", period = 60, color = "#ff7f0e", label = "P90" }],
            ["FraudDetection/${var.environment}", "transaction_processing_duration_ms", "application", local.fraud_detection_application, { stat = "p99", period = 60, color = "#d62728", label = "P99" }]
          ]
          view = "timeSeries"
          stacked = false
          yAxis = {
            left = {
              label = "Milli Seconds"
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 16
        y      = 42
        width  = 8
        height = 6
        properties = {
          title  = "Ring Buffer Utilization (CloudWatch)"
          region = var.aws_region
          metrics = [
            ["FraudDetection/${var.environment}", "ring_buffer_utilization_percent.value", "application", local.fraud_detection_application, { stat = "Average", period = 60, color = "#d62728", label = "Utilization %" }]
          ]
          view = "timeSeries"
          stacked = false
          yAxis = {
            left = {
              label = "Percentage"
              min = 0
              max = 100
            }
          }
        }
      },
      
    ]
  })
}

