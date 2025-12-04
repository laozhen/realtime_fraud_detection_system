# Setup and Build Guide

This guide provides comprehensive instructions for setting up your development environment, running tests locally, provisioning infrastructure, and triggering builds on GitHub.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Local Development Setup](#local-development-setup)
- [Running Tests Locally](#running-tests-locally)
- [Infrastructure Setup with Terraform](#infrastructure-setup-with-terraform)
- [GitHub CI/CD](#github-cicd)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

Ensure you have the following installed on your system:

| Tool | Version | Purpose |
|------|---------|---------|
| **Java JDK** | 21+ | Application runtime |
| **Gradle** | 8.x | Build automation (wrapper included) |
| **Docker** | 20.10+ | Container runtime (for Testcontainers) |
| **kubectl** | 1.28+ | Kubernetes CLI |
| **Helm** | 3.13+ | Kubernetes package manager |
| **Terraform** | 1.5+ | Infrastructure as Code |
| **AWS CLI** | 2.x | AWS operations |

### Optional Tools

- **LocalStack** - For local AWS service emulation

## Local Development Setup

### 1. Clone Repository

```powershell
git clone https://github.com/yourusername/hsbc_fraud_detection_system.git
cd hsbc_fraud_detection_system
```

### 2. Build the Project

```powershell
# Build all modules (excludes tests)
.\gradlew build -x test

# Or build with tests
.\gradlew build

# Clean and rebuild
.\gradlew clean build
```


## Running Tests Locally

The project uses a **two-tier testing strategy** to optimize test execution speed and Docker dependency.

### Test Architecture

```
📦 Tests
├── 🏃 Unit Tests (fast, no Docker)
│   ├── Rule tests
│   ├── Service tests
│   └── Basic integration tests
│
└── 🐳 Testcontainers Tests (requires Docker)
    ├── SQS integration tests
    └── End-to-end flow tests
```

### 1. Unit Tests (No Docker Required)

Unit tests run quickly without external dependencies:

```powershell
# Run all unit tests
.\gradlew test

# Run tests for specific module
.\gradlew :fraud-detection-service:test

# Run with coverage report
.\gradlew test jacocoTestReport

# View coverage report
start fraud-detection-service\build\reports\jacoco\test\html\index.html
```

**What gets tested:**
- Fraud detection rules (LargeAmountRule, SuspiciousAccountRule)
- Service layer logic with mocked dependencies
- Basic integration flows in-memory

### 2. Testcontainers Integration Tests (Requires Docker)

These tests use **Testcontainers** to spin up LocalStack for AWS SQS testing.

#### How Testcontainers Works

Testcontainers is a Java library that provides lightweight, throwaway instances of Docker containers for testing. In this project:

1. **Automatic Container Management**: Test starts → Docker container spins up → Tests run → Container tears down
2. **Real AWS Simulation**: Uses LocalStack to simulate SQS without needing actual AWS resources
3. **Isolated Environment**: Each test suite gets a fresh container instance

#### Example Test with Testcontainers

```java
@Tag("testcontainers")
@SpringBootTest()
@Testcontainers
@ActiveProfiles("sqs-integration")
class SqsIntegrationTest {
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(SQS);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Dynamically configure Spring to use Testcontainers endpoints
        registry.add("cloud.aws.sqs.endpoint", 
            () -> localstack.getEndpointOverride(SQS).toString());
        registry.add("spring.cloud.aws.credentials.access-key", 
            () -> localstack.getAccessKey());
        // ... more configuration
    }
}
```

#### Running Testcontainers Tests

```powershell
# Ensure Docker is running
docker ps

# Run Testcontainers tests
.\gradlew testcontainersTest

# Run all tests (unit + integration)
.\gradlew test testcontainersTest
```

**What gets tested:**
- SQS message publishing and consumption
- Complete end-to-end transaction flow
- AWS SDK integration
- Message serialization/deserialization

#### Testcontainers Test Configuration

Tests tagged with `@Tag("testcontainers")` are:
- **Excluded** from the standard `.\gradlew test` task
- **Included** in the `.\gradlew testcontainersTest` task
- Automatically ignored if Docker is not available

### 3. Test Reports

```powershell
# Generate test report
.\gradlew test

# View test results
start fraud-detection-service\build\reports\tests\test\index.html

# Generate coverage report
.\gradlew jacocoTestReport

# View coverage report
start fraud-detection-service\build\reports\jacoco\test\html\index.html
```

### 4. Continuous Testing (Watch Mode)

```powershell
# Run tests continuously on file changes
.\gradlew test --continuous
```

### Test Execution Flow

```mermaid
graph TD
    A[gradlew test] --> B[Unit Tests]
    B --> C[Fast Execution<br/>No Docker Required]
    
    D[gradlew testcontainersTest] --> E{Docker Running?}
    E -->|Yes| F[Start LocalStack Container]
    E -->|No| G[Tests Skipped]
    F --> H[Run Integration Tests]
    H --> I[Tear Down Container]
```

---

## Infrastructure Setup with Terraform

### Prerequisites

Before provisioning AWS infrastructure:

1. **AWS Account** with appropriate permissions
2. **AWS CLI** configured with credentials
3. **IAM User/Role** with EKS, SQS, CloudWatch permissions

### 1. Configure AWS Credentials

```powershell
# Configure AWS CLI (interactive)
aws configure

# Or set environment variables
$env:AWS_ACCESS_KEY_ID = "your-access-key"
$env:AWS_SECRET_ACCESS_KEY = "your-secret-key"
$env:AWS_DEFAULT_REGION = "us-east-1"

# Verify credentials
aws sts get-caller-identity
```

### 2. Prepare Terraform Configuration

```powershell
# Navigate to Terraform directory
cd terraform\aws

# Copy example variables file
Copy-Item terraform.tfvars.example terraform.tfvars

# Edit terraform.tfvars with your configuration
notepad terraform.tfvars
```

#### Key Configuration Options

Edit `terraform.tfvars`:

```hcl
# Basic Configuration
aws_region   = "us-east-1"
environment  = "test"  # or "prod"

# EKS Cluster Configuration
create_eks_cluster    = true  # Set to false if using existing cluster
eks_cluster_name      = "fraud-detection-cluster"
eks_node_instance_types = ["t3.medium"]
eks_node_capacity_type  = "ON_DEMAND"  # or "SPOT" for 60-90% cost savings
eks_node_desired_size = 2
eks_node_min_size     = 1
eks_node_max_size     = 5

# SQS Configuration
sqs_visibility_timeout = 30
sqs_message_retention  = 86400  # 1 day
sqs_max_receive_count  = 3

# CloudWatch Configuration
enable_cloudwatch_logs = true
log_retention_days     = 7

# Container Insights (optional but recommended)
enable_container_insights              = true
container_insights_log_retention_days  = 7

# GitHub Actions Deploy User (if using GitHub Actions)
github_actions_user_name = "aws_user"

# Tags
tags = {
  Team        = "Platform"
  CostCenter  = "Engineering"
  Application = "FraudDetection"
}
```

### 3. Initialize Terraform

```powershell
# Initialize Terraform (downloads providers)
terraform init

# Validate configuration
terraform validate

# Format configuration files
terraform fmt
```

### 4. Plan Infrastructure Changes

```powershell
# Preview changes (dry-run)
terraform plan

# Save plan to file
terraform plan -out=tfplan

# Review specific resource changes
terraform show tfplan
```

### 5. Apply Infrastructure

```powershell
# Apply changes (creates/updates resources)
terraform apply

# Or apply saved plan
terraform apply tfplan

# Auto-approve (skip confirmation - use with caution)
terraform apply -auto-approve
```

**What gets created:**

1. **EKS Cluster** (optional)
   - Kubernetes 1.31
   - Managed node groups
   - Pod Identity Agent addon
   - Container Insights (optional)

2. **SQS Queues**
   - `fraud-detection-queue` (main queue)
   - `fraud-detection-dlq` (dead-letter queue)

3. **IAM Resources**
   - Service account roles
   - EKS access policies
   - GitHub Actions user (optional)

4. **CloudWatch Resources**
   - Log groups
   - Metric filters
   - Container Insights dashboards

5. **VPC & Networking**
   - VPC with public/private subnets
   - NAT Gateways
   - Security groups

### 6. Configure kubectl for EKS

```powershell
# Update kubeconfig
aws eks update-kubeconfig --name fraud-detection-cluster --region us-east-1

# Verify connectivity
kubectl get nodes
kubectl get namespaces
```

### 7. Deploy Application to Kubernetes

```powershell
# Navigate to project root
cd ..\..

# Deploy with Helm
helm upgrade --install fraud-detection .\helm\fraud-detection `
  --namespace fraud-detection `
  --create-namespace `
  --values .\helm\values-aws-test.yaml

helm upgrade --install transaction-producer .\helm\transaction-producer `
  --namespace fraud-detection `
  --values .\helm\values-aws-test-producer.yaml

# Verify deployment
kubectl get pods -n fraud-detection
kubectl get svc -n fraud-detection
```

### 8. Terraform Outputs

After applying, Terraform outputs important information:

```powershell
# View all outputs
terraform output

# View specific output
terraform output sqs_queue_url
terraform output eks_cluster_endpoint
terraform output github_actions_access_key  # If created
```

## GitHub CI/CD

The project includes automated CI/CD workflows using GitHub Actions.

### Workflow Overview

```
📦 GitHub Actions Workflows
├── build-and-test.yml          # PR validation + build
├── deploy-to-aws-test-cluster.yml  # Deploy to test environment
├── rollback.yml                 # Rollback deployments
└── version.yml                  # Version management
```

### 1. Build and Test Workflow

**Trigger:** Pull request or push to `main`/`develop`

**What it does:**
1. Runs unit tests
2. Generates code coverage report
3. Performs code quality checks
4. Builds Docker images (on `main` branch)
5. Publishes images to GitHub Container Registry (GHCR)  (on `main` branch)
6. Packages and publishes Helm charts

**Manual Trigger:**

```powershell
# Using GitHub CLI
gh workflow run build-and-test.yml

# Or via GitHub UI
# Navigate to: Actions → Build and Test → Run workflow
```

### 2. Deploy to Test Environment

**Trigger:** Manual via workflow dispatch

**Prerequisites:**
- Infrastructure provisioned via Terraform
- Docker images already published to GHCR
- GitHub secrets configured

**Setup GitHub Secrets:**

Navigate to: **Settings → Secrets and variables → Actions**

Add these secrets:

| Secret Name | Description | Example |
|-------------|-------------|---------|
| `AWS_ACCESS_KEY_ID` | AWS access key for deployment | `AKIAIOSFODNN7EXAMPLE` |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key | `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY` |


**Via GitHub UI:**
1. Navigate to **Actions** tab
2. Select **Deploy to Test Environment**
3. Click **Run workflow**
4. Enter version (e.g., `1.0.0-dev.abc1234`)
5. Click **Run workflow**


### 4. Version Tags

The build workflow automatically generates version tags:

- **Format:** `1.0.0-dev.<short-sha>` (for `main` branch)
- **Example:** `1.0.0-dev.abc1234`

### 6. Docker Images

Built images are published to GitHub Container Registry:
[Docker Image Link](https://github.com/laozhen?tab=packages&repo_name=realtime_fraud_detection_system)

### 7. Helm Charts

Packaged charts are published to GHCR as OCI artifacts:

[Helm Package Link](https://github.com/laozhen?tab=packages&repo_name=realtime_fraud_detection_system)
### 8. CI/CD Pipeline Flow

```mermaid
graph TD
    A[PR Merged into main] --> B[Build & Test Workflow]
    B --> C[Run Tests]
    C --> D[Build Docker Images]
    D --> E[Push to GHCR]
    E --> F[Package Helm Charts]
    F --> G[Push Charts to GHCR]
    
    H[Manual Trigger] --> I[Deploy to Test Workflow]
    I --> J[Pull Docker Images from GHCR]
    J --> K[Pull Helm Charts from GHCR]
    K --> L[Deploy to EKS Test]
    L --> M[Run Smoke Tests]
    
    N[Manual Trigger] --> O[Rollback Workflow]
    O --> P[Helm Rollback]
```

### 9. Monitoring Deployments

```powershell
# Watch pods during deployment
kubectl get pods -n fraud-detection -w

# Check deployment status
kubectl rollout status deployment/fraud-detection -n fraud-detection

# View recent events
kubectl get events -n fraud-detection --sort-by='.lastTimestamp'

# View logs
kubectl logs -f deployment/fraud-detection -n fraud-detection
```

---


### Common Issues

#### 1. Docker Not Running (Testcontainers)

**Error:**
```
org.testcontainers.containers.ContainerLaunchException: Could not find a valid Docker environment
```

**Solution:**
```powershell
# Start Docker Desktop
# Verify Docker is running
docker ps

# Run tests again
.\gradlew testcontainersTest
```

#### 2. Gradle Build Fails

**Error:**
```
Could not resolve dependencies
```

**Solution:**
```powershell
# Clean build cache
.\gradlew clean --refresh-dependencies

# Rebuild
.\gradlew build
```

#### 3. AWS Credentials Not Found

**Error:**
```
Unable to load AWS credentials from any provider in the chain
```

**Solution:**
```powershell
# Reconfigure AWS CLI
aws configure

# Or set environment variables
$env:AWS_ACCESS_KEY_ID = "your-key"
$env:AWS_SECRET_ACCESS_KEY = "your-secret"
```

#### 4. Terraform State Locked

**Error:**
```
Error: state is locked by another process
```

**Solution:**
```powershell
# Force unlock (use with caution)
terraform force-unlock <lock-id>
```

#### 5. kubectl Not Connected to EKS

**Error:**
```
The connection to the server localhost:8080 was refused
```

**Solution:**
```powershell
# Update kubeconfig
aws eks update-kubeconfig --name fraud-detection-cluster --region us-east-1

# Verify
kubectl get nodes
```

#### 6. Helm Chart Not Found

**Error:**
```
Error: failed to download "oci://ghcr.io/..."
```

**Solution:**
```powershell
# Login to Helm registry
echo $env:GITHUB_TOKEN | helm registry login ghcr.io --username yourusername --password-stdin

# Verify chart exists
helm search repo fraud-detection
```

### Getting Help

- **Documentation:** [docs/ARCHITECTURE.md](ARCHITECTURE.md)
- **GitHub Issues:** [Project Issues](https://github.com/hsbc/fraud-detection-system/issues)
- **Logs:** Check CloudWatch Logs in AWS Console

--