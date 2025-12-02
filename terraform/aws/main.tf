terraform {
  required_version = ">= 1.5.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  
}

provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = {
      Project     = "fraud-detection-system"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# Data sources
data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  queue_name = "fraud-detection-queue-${var.environment}"
  dlq_name   = "fraud-detection-dlq-${var.environment}"
}

