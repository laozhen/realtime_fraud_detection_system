terraform {
  required_version = ">= 1.5.0"
  
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
  
  # Uncomment for remote state
  # backend "gcs" {
  #   bucket = "fraud-detection-terraform-state"
  #   prefix = "terraform/state"
  # }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
}

locals {
  topic_name        = "fraud-detection-topic-${var.environment}"
  subscription_name = "fraud-detection-sub-${var.environment}"
  dlq_topic_name    = "fraud-detection-dlq-${var.environment}"
  dlq_sub_name      = "fraud-detection-dlq-sub-${var.environment}"
}

