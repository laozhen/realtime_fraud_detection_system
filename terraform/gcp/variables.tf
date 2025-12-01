variable "gcp_project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "gcp_region" {
  description = "GCP region for resources"
  type        = string
  default     = "us-central1"
}

variable "environment" {
  description = "Environment name (test, prod)"
  type        = string
  default     = "test"
  
  validation {
    condition     = contains(["test", "prod"], var.environment)
    error_message = "Environment must be either 'test' or 'prod'."
  }
}

variable "create_gke_cluster" {
  description = "Whether to create a new GKE cluster"
  type        = bool
  default     = false
}

variable "gke_cluster_name" {
  description = "Name of the GKE cluster"
  type        = string
  default     = "fraud-detection-cluster"
}

variable "gke_node_count" {
  description = "Number of GKE nodes"
  type        = number
  default     = 2
}

variable "gke_machine_type" {
  description = "Machine type for GKE nodes"
  type        = string
  default     = "e2-medium"
}

variable "pubsub_message_retention" {
  description = "Pub/Sub message retention duration"
  type        = string
  default     = "86400s"  # 1 day
}

variable "pubsub_ack_deadline" {
  description = "Pub/Sub acknowledgement deadline in seconds"
  type        = number
  default     = 20
}

variable "pubsub_max_delivery_attempts" {
  description = "Maximum delivery attempts before DLQ"
  type        = number
  default     = 5
}

variable "enable_stackdriver_logging" {
  description = "Enable Stackdriver logging"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "Stackdriver log retention in days"
  type        = number
  default     = 7
}

variable "labels" {
  description = "Labels to apply to resources"
  type        = map(string)
  default     = {}
}

