variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
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

variable "eks_cluster_name" {
  description = "Name of the EKS cluster (if creating new cluster)"
  type        = string
  default     = ""
}

variable "existing_cluster_name" {
  description = "Name of existing EKS cluster (used when create_eks_cluster is false)"
  type        = string
  default     = ""
}

variable "existing_oidc_provider_arn" {
  description = "ARN of existing OIDC provider for EKS cluster (used when create_eks_cluster is false)"
  type        = string
  default     = ""
}

variable "create_eks_cluster" {
  description = "Whether to create a new EKS cluster"
  type        = bool
  default     = false
}

variable "eks_node_instance_types" {
  description = "Instance types for EKS nodes"
  type        = list(string)
  default     = ["t3.medium"]
}

variable "eks_node_capacity_type" {
  description = "Capacity type for EKS nodes (ON_DEMAND or SPOT)"
  type        = string
  default     = "ON_DEMAND"
  
  validation {
    condition     = contains(["ON_DEMAND", "SPOT"], var.eks_node_capacity_type)
    error_message = "Capacity type must be either 'ON_DEMAND' or 'SPOT'."
  }
}

variable "eks_node_desired_size" {
  description = "Desired number of EKS nodes"
  type        = number
  default     = 2
}

variable "eks_node_min_size" {
  description = "Minimum number of EKS nodes"
  type        = number
  default     = 1
}

variable "eks_node_max_size" {
  description = "Maximum number of EKS nodes"
  type        = number
  default     = 5
}

# EKS Addon Versions
variable "pod_identity_agent_version" {
  description = "Version of EKS Pod Identity Agent addon"
  type        = string
  default     = "v1.3.4-eksbuild.1"
}

variable "vpc_cni_version" {
  description = "Version of VPC CNI addon"
  type        = string
  default     = "v1.19.0-eksbuild.1"
}

variable "coredns_version" {
  description = "Version of CoreDNS addon"
  type        = string
  default     = "v1.11.4-eksbuild.2"
}

variable "kube_proxy_version" {
  description = "Version of kube-proxy addon"
  type        = string
  default     = "v1.31.3-eksbuild.2"
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "sqs_visibility_timeout" {
  description = "SQS visibility timeout in seconds"
  type        = number
  default     = 30
}

variable "sqs_message_retention" {
  description = "SQS message retention period in seconds"
  type        = number
  default     = 86400  # 1 day
}

variable "sqs_max_receive_count" {
  description = "Maximum receives before moving to DLQ"
  type        = number
  default     = 3
}

variable "enable_cloudwatch_logs" {
  description = "Enable CloudWatch logs"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 7
}

variable "cloudwatch_kms_key_id" {
  description = "KMS key ID for CloudWatch log group encryption (optional)"
  type        = string
  default     = null
}

variable "fraud_alert_threshold" {
  description = "Number of fraud detections per 5 minutes to trigger alarm"
  type        = number
  default     = 50
}

variable "error_alert_threshold" {
  description = "Number of errors per 5 minutes to trigger alarm"
  type        = number
  default     = 20
}

variable "tags" {
  description = "Additional tags for resources"
  type        = map(string)
  default     = {}
}

# ============================================================================
# GitHub Actions Deploy User Configuration
# ============================================================================

variable "create_github_actions_user" {
  description = "Whether to create IAM user for GitHub Actions deployment"
  type        = bool
  default     = false
}

variable "github_actions_user_name" {
  description = "Name of IAM user for GitHub Actions deployment"
  type        = string
  default     = "aws_user"
}

variable "create_github_actions_access_key" {
  description = "Whether to create access key for GitHub Actions user (set to false if managing keys externally)"
  type        = bool
  default     = true
}

# ============================================================================
# CloudWatch Alarms Configuration
# ============================================================================

variable "alarm_sns_topic_arn" {
  description = "SNS topic ARN for CloudWatch alarms (leave empty to disable alarm notifications)"
  type        = string
  default     = ""
}

# ============================================================================
# Container Insights Configuration
# ============================================================================

variable "enable_container_insights" {
  description = "Enable Container Insights for EKS cluster monitoring"
  type        = bool
  default     = true
}

variable "container_insights_log_retention_days" {
  description = "Retention period for Container Insights logs in days"
  type        = number
  default     = 7
}

variable "cloudwatch_observability_addon_version" {
  description = "Version of CloudWatch Observability addon for Container Insights"
  type        = string
  default     = "v4.7.0-eksbuild.1"
}

