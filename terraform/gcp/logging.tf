# Log sinks for structured logging

resource "google_logging_project_sink" "fraud_detection_logs" {
  count       = var.enable_stackdriver_logging ? 1 : 0
  name        = "fraud-detection-${var.environment}-sink"
  destination = "logging.googleapis.com/projects/${var.gcp_project_id}/logs/fraud-detection-${var.environment}"
  
  filter = <<-EOT
    resource.type="k8s_container"
    resource.labels.namespace_name="fraud-detection"
    resource.labels.container_name="fraud-detection-service"
  EOT
  
  unique_writer_identity = true
}

# Log-based metrics for alerting
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
  }
  
  label_extractors = {
    "severity" = "EXTRACT(jsonPayload.severity)"
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

