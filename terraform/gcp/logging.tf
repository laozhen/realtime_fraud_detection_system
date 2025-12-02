# Log sinks for structured logging with distributed tracing support

resource "google_logging_project_sink" "fraud_detection_logs" {
  count       = var.enable_stackdriver_logging ? 1 : 0
  name        = "fraud-detection-service-${var.environment}-sink"
  destination = "logging.googleapis.com/projects/${var.gcp_project_id}/logs/fraud-detection-${var.environment}"
  
  filter = <<-EOT
    resource.type="k8s_container"
    resource.labels.namespace_name="fraud-detection"
    resource.labels.container_name="fraud-detection-service"
  EOT
  
  unique_writer_identity = true
}

resource "google_logging_project_sink" "transaction_producer_logs" {
  count       = var.enable_stackdriver_logging ? 1 : 0
  name        = "transaction-producer-${var.environment}-sink"
  destination = "logging.googleapis.com/projects/${var.gcp_project_id}/logs/transaction-producer-${var.environment}"
  
  filter = <<-EOT
    resource.type="k8s_container"
    resource.labels.namespace_name="fraud-detection"
    resource.labels.container_name="transaction-producer"
  EOT
  
  unique_writer_identity = true
}

# Log views for easier querying
resource "google_logging_log_view" "fraud_detection_view" {
  count       = var.enable_stackdriver_logging ? 1 : 0
  name        = "fraud-detection-${var.environment}-view"
  bucket      = "_Default"
  parent      = "projects/${var.gcp_project_id}/locations/global/buckets/_Default"
  description = "Log view for fraud detection services with correlation tracking"
  
  filter = <<-EOT
    resource.labels.namespace_name="fraud-detection"
    (resource.labels.container_name="fraud-detection-service" OR
     resource.labels.container_name="transaction-producer")
  EOT
}

# Log-based metrics for alerting with enhanced correlation tracking
resource "google_logging_metric" "fraud_alert_count" {
  name   = "fraud_alert_count_${var.environment}"
  filter = <<-EOT
    resource.type="k8s_container"
    resource.labels.namespace_name="fraud-detection"
    jsonPayload.message=~"FRAUD_DETECTED"
  EOT
  
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    unit        = "1"
    
    labels {
      key         = "severity"
      value_type  = "STRING"
      description = "Fraud severity level"
    }
    
    labels {
      key         = "environment"
      value_type  = "STRING"
      description = "Environment (test/prod)"
    }
  }
  
  label_extractors = {
    "severity"    = "EXTRACT(jsonPayload.severity)"
    "environment" = "EXTRACT(jsonPayload.environment)"
  }
}

# Metric for high latency transactions
resource "google_logging_metric" "high_latency_count" {
  name   = "high_latency_count_${var.environment}"
  filter = <<-EOT
    resource.type="k8s_container"
    resource.labels.namespace_name="fraud-detection"
    jsonPayload.message=~"High latency detected"
  EOT
  
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    unit        = "1"
  }
}

# Metric for error count
resource "google_logging_metric" "error_count" {
  name   = "error_count_${var.environment}"
  filter = <<-EOT
    resource.type="k8s_container"
    resource.labels.namespace_name="fraud-detection"
    severity="ERROR"
  EOT
  
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    unit        = "1"
    
    labels {
      key         = "service"
      value_type  = "STRING"
      description = "Service name"
    }
  }
  
  label_extractors = {
    "service" = "EXTRACT(jsonPayload.serviceName)"
  }
}

# Alert policy for high fraud detection rate
resource "google_monitoring_alert_policy" "high_fraud_rate" {
  display_name = "High Fraud Detection Rate - ${var.environment}"
  combiner     = "OR"
  
  conditions {
    display_name = "Fraud detection rate exceeds threshold"
    
    condition_threshold {
      filter          = "metric.type=\"logging.googleapis.com/user/fraud_alert_count_${var.environment}\" resource.type=\"k8s_container\""
      duration        = "300s"
      comparison      = "COMPARISON_GT"
      threshold_value = 10
      
      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_RATE"
      }
    }
  }
  
  notification_channels = []  # Add your notification channels here
  
  alert_strategy {
    auto_close = "1800s"
  }
}

# Alert policy for DLQ messages
resource "google_monitoring_alert_policy" "dlq_messages" {
  display_name = "Messages in Dead Letter Queue - ${var.environment}"
  combiner     = "OR"
  
  conditions {
    display_name = "DLQ has unacknowledged messages"
    
    condition_threshold {
      filter          = "resource.type=\"pubsub_subscription\" AND resource.labels.subscription_id=\"${local.dlq_sub_name}\" AND metric.type=\"pubsub.googleapis.com/subscription/num_undelivered_messages\""
      duration        = "60s"
      comparison      = "COMPARISON_GT"
      threshold_value = 0
      
      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_MEAN"
      }
    }
  }
  
  notification_channels = []  # Add your notification channels here
}

