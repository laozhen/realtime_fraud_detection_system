# Data source for project info
data "google_project" "project" {}

# Service Account for Fraud Detection Service
resource "google_service_account" "fraud_detection" {
  account_id   = "fraud-detection-${var.environment}"
  display_name = "Fraud Detection Service ${var.environment}"
  description  = "Service account for fraud detection microservice"
}

# Service Account for Transaction Producer
resource "google_service_account" "transaction_producer" {
  account_id   = "transaction-producer-${var.environment}"
  display_name = "Transaction Producer ${var.environment}"
  description  = "Service account for transaction producer microservice"
}

# Pub/Sub Publisher role for Transaction Producer
resource "google_pubsub_topic_iam_member" "producer_publisher" {
  topic  = google_pubsub_topic.fraud_detection.id
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:${google_service_account.transaction_producer.email}"
}

# Pub/Sub Subscriber role for Fraud Detection Service
resource "google_pubsub_subscription_iam_member" "fraud_detection_subscriber" {
  subscription = google_pubsub_subscription.fraud_detection.id
  role         = "roles/pubsub.subscriber"
  member       = "serviceAccount:${google_service_account.fraud_detection.email}"
}

# Viewer role for getting subscription info
resource "google_pubsub_subscription_iam_member" "fraud_detection_viewer" {
  subscription = google_pubsub_subscription.fraud_detection.id
  role         = "roles/pubsub.viewer"
  member       = "serviceAccount:${google_service_account.fraud_detection.email}"
}

# Workload Identity binding for Fraud Detection Service
resource "google_service_account_iam_member" "fraud_detection_workload_identity" {
  count              = var.create_gke_cluster ? 1 : 0
  service_account_id = google_service_account.fraud_detection.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.gcp_project_id}.svc.id.goog[fraud-detection/fraud-detection-service]"
}

# Workload Identity binding for Transaction Producer
resource "google_service_account_iam_member" "transaction_producer_workload_identity" {
  count              = var.create_gke_cluster ? 1 : 0
  service_account_id = google_service_account.transaction_producer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.gcp_project_id}.svc.id.goog[fraud-detection/transaction-producer]"
}

# Logging permissions
resource "google_project_iam_member" "fraud_detection_logging" {
  count   = var.enable_stackdriver_logging ? 1 : 0
  project = var.gcp_project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.fraud_detection.email}"
}

resource "google_project_iam_member" "transaction_producer_logging" {
  count   = var.enable_stackdriver_logging ? 1 : 0
  project = var.gcp_project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.transaction_producer.email}"
}

# Monitoring permissions
resource "google_project_iam_member" "fraud_detection_monitoring" {
  project = var.gcp_project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.fraud_detection.email}"
}

resource "google_project_iam_member" "transaction_producer_monitoring" {
  project = var.gcp_project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.transaction_producer.email}"
}

