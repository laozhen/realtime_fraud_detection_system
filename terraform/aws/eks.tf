# This file contains EKS cluster configuration (optional - can use existing cluster)
# This is a MINIMAL configuration for testing/development purposes

# Get available AZs
data "aws_availability_zones" "available" {
  count = var.create_eks_cluster ? 1 : 0
  state = "available"
}

# VPC for EKS
resource "aws_vpc" "eks_vpc" {
  count                = var.create_eks_cluster ? 1 : 0
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  
  tags = merge(
    var.tags,
    {
      Name = "fraud-detection-vpc-${var.environment}"
      "kubernetes.io/cluster/${var.eks_cluster_name}-${var.environment}" = "shared"
    }
  )
}

# Internet Gateway
resource "aws_internet_gateway" "eks_igw" {
  count  = var.create_eks_cluster ? 1 : 0
  vpc_id = aws_vpc.eks_vpc[0].id
  
  tags = merge(
    var.tags,
    {
      Name = "fraud-detection-igw-${var.environment}"
    }
  )
}

# Public Subnets (2 AZs minimum for EKS)
resource "aws_subnet" "eks_public" {
  count                   = var.create_eks_cluster ? 2 : 0
  vpc_id                  = aws_vpc.eks_vpc[0].id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone       = data.aws_availability_zones.available[0].names[count.index]
  map_public_ip_on_launch = true
  
  tags = merge(
    var.tags,
    {
      Name                                            = "fraud-detection-public-${count.index + 1}-${var.environment}"
      "kubernetes.io/cluster/${var.eks_cluster_name}-${var.environment}" = "shared"
      "kubernetes.io/role/elb"                        = "1"
    }
  )
}

# Route Table for Public Subnets
resource "aws_route_table" "eks_public" {
  count  = var.create_eks_cluster ? 1 : 0
  vpc_id = aws_vpc.eks_vpc[0].id
  
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.eks_igw[0].id
  }
  
  tags = merge(
    var.tags,
    {
      Name = "fraud-detection-public-rt-${var.environment}"
    }
  )
}

# Route Table Associations
resource "aws_route_table_association" "eks_public" {
  count          = var.create_eks_cluster ? 2 : 0
  subnet_id      = aws_subnet.eks_public[count.index].id
  route_table_id = aws_route_table.eks_public[0].id
}

# Security Group for EKS Cluster
resource "aws_security_group" "eks_cluster" {
  count       = var.create_eks_cluster ? 1 : 0
  name        = "fraud-detection-eks-cluster-sg-${var.environment}"
  description = "Security group for EKS cluster"
  vpc_id      = aws_vpc.eks_vpc[0].id
  
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = merge(
    var.tags,
    {
      Name = "fraud-detection-eks-cluster-sg-${var.environment}"
    }
  )
}

# Security Group for EKS Nodes
resource "aws_security_group" "eks_nodes" {
  count       = var.create_eks_cluster ? 1 : 0
  name        = "fraud-detection-eks-nodes-sg-${var.environment}"
  description = "Security group for EKS nodes"
  vpc_id      = aws_vpc.eks_vpc[0].id
  
  ingress {
    description = "Allow nodes to communicate with each other"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }
  
  ingress {
    description     = "Allow cluster to communicate with nodes"
    from_port       = 1025
    to_port         = 65535
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_cluster[0].id]
  }
  
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = merge(
    var.tags,
    {
      Name = "fraud-detection-eks-nodes-sg-${var.environment}"
    }
  )
}

# IAM Role for EKS Cluster
resource "aws_iam_role" "eks_cluster" {
  count = var.create_eks_cluster ? 1 : 0
  name  = "fraud-detection-eks-cluster-${var.environment}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "eks.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
  
  tags = var.tags
}

# Attach required policies to EKS Cluster role
resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  count      = var.create_eks_cluster ? 1 : 0
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks_cluster[0].name
}

resource "aws_iam_role_policy_attachment" "eks_vpc_resource_controller" {
  count      = var.create_eks_cluster ? 1 : 0
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSVPCResourceController"
  role       = aws_iam_role.eks_cluster[0].name
}

# IAM Role for EKS Node Group
resource "aws_iam_role" "eks_nodes" {
  count = var.create_eks_cluster ? 1 : 0
  name  = "fraud-detection-eks-nodes-${var.environment}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
  
  tags = var.tags
}

# Attach required policies to EKS Node role
resource "aws_iam_role_policy_attachment" "eks_worker_node_policy" {
  count      = var.create_eks_cluster ? 1 : 0
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.eks_nodes[0].name
}

resource "aws_iam_role_policy_attachment" "eks_cni_policy" {
  count      = var.create_eks_cluster ? 1 : 0
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.eks_nodes[0].name
}

resource "aws_iam_role_policy_attachment" "eks_container_registry_policy" {
  count      = var.create_eks_cluster ? 1 : 0
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.eks_nodes[0].name
}

# EKS Cluster
resource "aws_eks_cluster" "main" {
  count    = var.create_eks_cluster ? 1 : 0
  name     = "${var.eks_cluster_name}-${var.environment}"
  role_arn = aws_iam_role.eks_cluster[0].arn
  version  = "1.34"
  
  vpc_config {
    subnet_ids              = aws_subnet.eks_public[*].id
    endpoint_public_access  = true
    endpoint_private_access = false
    security_group_ids      = [aws_security_group.eks_cluster[0].id]
  }
  
  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster_policy,
    aws_iam_role_policy_attachment.eks_vpc_resource_controller,
  ]
  
  tags = var.tags
}

# EKS Node Group
resource "aws_eks_node_group" "main" {
  count           = var.create_eks_cluster ? 1 : 0
  cluster_name    = aws_eks_cluster.main[0].name
  node_group_name = "fraud-detection-nodes-${var.environment}"
  node_role_arn   = aws_iam_role.eks_nodes[0].arn
  subnet_ids      = aws_subnet.eks_public[*].id
  
  scaling_config {
    desired_size = var.eks_node_desired_size
    max_size     = var.eks_node_max_size
    min_size     = var.eks_node_min_size
  }
  
  instance_types = var.eks_node_instance_types
  capacity_type  = var.eks_node_capacity_type  # SPOT or ON_DEMAND
  
  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node_policy,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.eks_container_registry_policy,
  ]
  
  tags = merge(
    var.tags,
    {
      Name = "fraud-detection-node-group-${var.environment}"
    }
  )
}

# OIDC Provider for EKS (for IRSA - IAM Roles for Service Accounts)
resource "aws_iam_openid_connect_provider" "eks" {
  count = var.create_eks_cluster ? 1 : 0
  
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["9e99a48a9960b14926bb7f3b02e22da2b0ab7280"]
  url             = aws_eks_cluster.main[0].identity[0].oidc[0].issuer
  
  tags = var.tags
}

