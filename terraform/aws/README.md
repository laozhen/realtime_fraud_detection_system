# AWS Infrastructure for Fraud Detection System

This Terraform configuration sets up the AWS infrastructure required for the fraud detection system.

## Resources Created

- **SQS Queue**: Main queue for transaction messages
- **SQS DLQ**: Dead letter queue for failed messages
- **IAM Roles**: Service accounts for EKS pods with IRSA
- **CloudWatch Log Groups**: For application logs
- **CloudWatch Alarms**: Monitoring for queue health

## Prerequisites

1. AWS CLI configured with appropriate credentials
2. Terraform >= 1.5.0
3. An existing EKS cluster (or set `create_eks_cluster = true`)

## Usage

```bash
# Initialize Terraform
terraform init

# Copy and customize variables
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your values

# Plan the deployment
terraform plan

# Apply the configuration
terraform apply

# Get outputs
terraform output
```

## Important Notes

### IRSA (IAM Roles for Service Accounts)

The IAM roles are configured for IRSA. Update your Helm values with:

```yaml
serviceAccount:
  annotations:
    eks.amazonaws.com/role-arn: <fraud_detection_role_arn>
```

### Using Existing EKS Cluster

If using an existing cluster, set `create_eks_cluster = false` and manually update the OIDC provider URL in `iam.tf`.

### Cost Considerations

- EKS Control Plane: ~$73/month
- Worker Nodes: Depends on instance types
- SQS: $0.40 per million requests (first 1M free)
- CloudWatch Logs: $0.50 per GB ingested

## Outputs

After applying, you'll get:
- SQS queue URLs and ARNs
- IAM role ARNs for Kubernetes service accounts
- CloudWatch log group names

## Cleanup

```bash
terraform destroy
```

## Security Best Practices

1. Enable SQS encryption at rest
2. Use VPC endpoints for private connectivity
3. Implement least privilege IAM policies
4. Enable CloudTrail for audit logging
5. Use AWS Secrets Manager for sensitive data

