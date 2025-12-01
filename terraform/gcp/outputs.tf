output "pubsub_topic_name" {
  description = "Name of the Pub/Sub topic"
  value       = google_pubsub_topic.fraud_detection.name
}

output "pubsub_topic_id" {
  description = "ID of the Pub/Sub topic"
  value       = google_pubsub_topic.fraud_detection.id
}

output "pubsub_subscription_name" {
  description = "Name of the Pub/Sub subscription"
  value       = google_pubsub_subscription.fraud_detection.name
}

output "pubsub_subscription_id" {
  description = "ID of the Pub/Sub subscription"
  value       = google_pubsub_subscription.fraud_detection.id
}

output "pubsub_dlq_topic_name" {
  description = "Name of the DLQ topic"
  value       = google_pubsub_topic.fraud_detection_dlq.name
}

output "fraud_detection_service_account_email" {
  description = "Email of the fraud detection service account"
  value       = google_service_account.fraud_detection.email
}

output "transaction_producer_service_account_email" {
  description = "Email of the transaction producer service account"
  value       = google_service_account.transaction_producer.email
}

output "gke_cluster_name" {
  description = "Name of the GKE cluster"
  value       = var.create_gke_cluster ? google_container_cluster.fraud_detection[0].name : ""
}

output "gke_cluster_endpoint" {
  description = "Endpoint of the GKE cluster"
  value       = var.create_gke_cluster ? google_container_cluster.fraud_detection[0].endpoint : ""
  sensitive   = true
}

output "project_id" {
  description = "GCP Project ID"
  value       = var.gcp_project_id
}

output "region" {
  description = "GCP region"
  value       = var.gcp_region
}

