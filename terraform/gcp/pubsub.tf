# Dead Letter Topic
resource "google_pubsub_topic" "fraud_detection_dlq" {
  name = local.dlq_topic_name
  
  message_retention_duration = "604800s"  # 7 days
  
  labels = merge(
    var.labels,
    {
      environment = var.environment
      type        = "dlq"
    }
  )
}

# Dead Letter Subscription
resource "google_pubsub_subscription" "fraud_detection_dlq_sub" {
  name  = local.dlq_sub_name
  topic = google_pubsub_topic.fraud_detection_dlq.name
  
  message_retention_duration = "604800s"  # 7 days
  ack_deadline_seconds       = 60
  
  labels = merge(
    var.labels,
    {
      environment = var.environment
      type        = "dlq"
    }
  )
}

# Main Topic
resource "google_pubsub_topic" "fraud_detection" {
  name = local.topic_name
  
  message_retention_duration = var.pubsub_message_retention
  
  labels = merge(
    var.labels,
    {
      environment = var.environment
      type        = "main"
    }
  )
}

# Main Subscription with DLQ
resource "google_pubsub_subscription" "fraud_detection" {
  name  = local.subscription_name
  topic = google_pubsub_topic.fraud_detection.name
  
  message_retention_duration = var.pubsub_message_retention
  ack_deadline_seconds       = var.pubsub_ack_deadline
  
  # Enable exactly once delivery
  enable_exactly_once_delivery = true
  
  # Dead letter policy
  dead_letter_policy {
    dead_letter_topic     = google_pubsub_topic.fraud_detection_dlq.id
    max_delivery_attempts = var.pubsub_max_delivery_attempts
  }
  
  # Retry policy
  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }
  
  labels = merge(
    var.labels,
    {
      environment = var.environment
      type        = "main"
    }
  )
}

# IAM binding for DLQ
resource "google_pubsub_topic_iam_member" "dlq_publisher" {
  topic  = google_pubsub_topic.fraud_detection_dlq.id
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:service-${data.google_project.project.number}@gcp-sa-pubsub.iam.gserviceaccount.com"
}

resource "google_pubsub_subscription_iam_member" "dlq_subscriber" {
  subscription = google_pubsub_subscription.fraud_detection_dlq_sub.id
  role         = "roles/pubsub.subscriber"
  member       = "serviceAccount:service-${data.google_project.project.number}@gcp-sa-pubsub.iam.gserviceaccount.com"
}

