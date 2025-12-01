# This file contains EKS cluster configuration (optional - can use existing cluster)

# Note: Creating a full EKS cluster is expensive for a demo.
# You may want to use an existing cluster and just reference it.

# OIDC Provider for EKS (for IRSA)
resource "aws_iam_openid_connect_provider" "eks" {
  count = var.create_eks_cluster ? 1 : 0
  
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["9e99a48a9960b14926bb7f3b02e22da2b0ab7280"]  # Current EKS OIDC thumbprint
  url             = "https://oidc.eks.${var.aws_region}.amazonaws.com/id/${var.eks_cluster_name}"
  
  tags = var.tags
}

# VPC for EKS (simplified)
resource "aws_vpc" "eks_vpc" {
  count                = var.create_eks_cluster ? 1 : 0
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  
  tags = merge(
    var.tags,
    {
      Name = "fraud-detection-vpc-${var.environment}"
      "kubernetes.io/cluster/${var.eks_cluster_name}" = "shared"
    }
  )
}

# Note: For a complete EKS setup, you would need:
# - Subnets (public and private)
# - Internet Gateway
# - NAT Gateway
# - Route Tables
# - Security Groups
# - EKS Cluster
# - Node Group
# 
# This is intentionally simplified for the demo.
# In production, use a module like terraform-aws-modules/eks/aws

