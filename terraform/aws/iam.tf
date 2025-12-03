# Data source to get existing EKS cluster OIDC provider (when not creating cluster)
data "aws_eks_cluster" "existing" {
  count = var.create_eks_cluster ? 0 : 1
  name  = var.existing_cluster_name != "" ? var.existing_cluster_name : "fraud-detection-cluster-${var.environment}"
}

# Extract OIDC issuer URL
locals {
  oidc_provider_arn = var.create_eks_cluster ? aws_iam_openid_connect_provider.eks[0].arn : var.existing_oidc_provider_arn
  oidc_provider_url = var.create_eks_cluster ? replace(aws_eks_cluster.main[0].identity[0].oidc[0].issuer, "https://", "") : (var.existing_oidc_provider_arn != "" ? replace(var.existing_oidc_provider_arn, "arn:${data.aws_partition.current.partition}:iam::${local.account_id}:oidc-provider/", "") : "")
}

# IAM Role for Fraud Detection Service
resource "aws_iam_role" "fraud_detection_role" {
  name = "fraud-detection-service-${var.environment}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Federated = local.oidc_provider_arn
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "${local.oidc_provider_url}:sub" = "system:serviceaccount:fraud-detection:fraud-detection"
            "${local.oidc_provider_url}:aud" = "sts.amazonaws.com"
          }
        }
      }
    ]
  })
  
  tags = var.tags
}

# IAM Policy for Fraud Detection Service - SQS Consumer
resource "aws_iam_role_policy" "fraud_detection_sqs_policy" {
  name = "sqs-consumer-policy"
  role = aws_iam_role.fraud_detection_role.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes",
          "sqs:GetQueueUrl",
          "sqs:ChangeMessageVisibility"
        ]
        Resource = aws_sqs_queue.fraud_detection_queue.arn
      }
    ]
  })
}

# IAM Role for Transaction Producer Service
resource "aws_iam_role" "transaction_producer_role" {
  name = "transaction-producer-${var.environment}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Federated = local.oidc_provider_arn
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "${local.oidc_provider_url}:sub" = "system:serviceaccount:fraud-detection:transaction-producer"
            "${local.oidc_provider_url}:aud" = "sts.amazonaws.com"
          }
        }
      }
    ]
  })
  
  tags = var.tags
}

# IAM Policy for Transaction Producer - SQS Publisher
resource "aws_iam_role_policy" "transaction_producer_sqs_policy" {
  name = "sqs-publisher-policy"
  role = aws_iam_role.transaction_producer_role.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sqs:SendMessage",
          "sqs:GetQueueAttributes",
          "sqs:GetQueueUrl"
        ]
        Resource = aws_sqs_queue.fraud_detection_queue.arn
      }
    ]
  })
}

# CloudWatch Logs policy for Fraud Detection Service
resource "aws_iam_role_policy" "fraud_detection_logs_policy" {
  count = var.enable_cloudwatch_logs ? 1 : 0
  name  = "cloudwatch-logs-policy"
  role  = aws_iam_role.fraud_detection_role.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams",
          "logs:DescribeLogGroups"
        ]
        Resource = [
          "arn:${data.aws_partition.current.partition}:logs:${var.aws_region}:${local.account_id}:log-group:/aws/fraud-detection/${var.environment}/fraud-detection-service",
          "arn:${data.aws_partition.current.partition}:logs:${var.aws_region}:${local.account_id}:log-group:/aws/fraud-detection/${var.environment}/fraud-detection-service:*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "cloudwatch:PutMetricData"
        ]
        Resource = "*"
        Condition = {
          StringLike = {
            "cloudwatch:namespace" = "FraudDetection/*"
          }
        }
      }
    ]
  })
}

# CloudWatch Logs policy for Transaction Producer Service
resource "aws_iam_role_policy" "transaction_producer_logs_policy" {
  count = var.enable_cloudwatch_logs ? 1 : 0
  name  = "cloudwatch-logs-policy"
  role  = aws_iam_role.transaction_producer_role.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams",
          "logs:DescribeLogGroups"
        ]
        Resource = [
          "arn:${data.aws_partition.current.partition}:logs:${var.aws_region}:${local.account_id}:log-group:/aws/fraud-detection/${var.environment}/transaction-producer",
          "arn:${data.aws_partition.current.partition}:logs:${var.aws_region}:${local.account_id}:log-group:/aws/fraud-detection/${var.environment}/transaction-producer:*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "cloudwatch:PutMetricData"
        ]
        Resource = "*"
        Condition = {
          StringLike = {
            "cloudwatch:namespace" = "FraudDetection/*"
          }
        }
      }
    ]
  })
}

# ============================================================================
# GitHub Actions EKS Deploy User Policy
# ============================================================================

# Data source to reference existing IAM user for GitHub Actions deployment
data "aws_iam_user" "github_actions_user" {
  count     = var.github_actions_user_name != "" ? 1 : 0
  user_name = var.github_actions_user_name
}

# IAM Policy for GitHub Actions to deploy to EKS
resource "aws_iam_policy" "github_actions_eks_deploy" {
  count       = var.github_actions_user_name != "" ? 1 : 0
  name        = "GithubActionsEKSDeployPolicy-${var.environment}"
  description = "Policy for GitHub Actions to deploy to EKS cluster in ${var.environment} environment"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "EKSDescribeAccess"
        Effect = "Allow"
        Action = [
          "eks:DescribeCluster",
          "eks:ListClusters",
          "eks:AccessKubernetesApi"

        ]
        Resource = [
          "arn:${data.aws_partition.current.partition}:eks:${var.aws_region}:${local.account_id}:cluster/fraud-detection-cluster-${var.environment}"
        ]
      },
      {
        Sid    = "EC2NetworkDiscovery"
        Effect = "Allow"
        Action = [
          "ec2:DescribeSecurityGroups",
          "ec2:DescribeSubnets",
          "ec2:DescribeVpcs"
        ]
        Resource = "*"
      }
    ]
  })

  tags = merge(var.tags, {
    Purpose = "GitHub Actions EKS Deployment"
  })
}

# Attach the EKS deploy policy to the GitHub Actions user
resource "aws_iam_user_policy_attachment" "github_actions_eks_deploy" {
  count      = var.github_actions_user_name != "" ? 1 : 0
  user       = data.aws_iam_user.github_actions_user[0].user_name
  policy_arn = aws_iam_policy.github_actions_eks_deploy[0].arn
}

