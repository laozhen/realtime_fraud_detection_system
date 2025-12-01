# GCP Infrastructure for Fraud Detection System

This Terraform configuration sets up the GCP infrastructure required for the fraud detection system.

## Resources Created

- **Pub/Sub Topic**: Main topic for transaction messages
- **Pub/Sub Subscription**: Subscription with DLQ policy
- **Pub/Sub DLQ**: Dead letter topic and subscription
- **Service Accounts**: For fraud detection and transaction producer with Workload Identity
- **Log Sinks**: For structured logging
- **Monitoring Alerts**: For fraud detection and DLQ monitoring
- **GKE Cluster** (optional): Kubernetes cluster with Workload Identity

## Prerequisites

1. GCP Project with billing enabled
2. gcloud CLI configured
3. Terraform >= 1.5.0
4. Required APIs enabled:
   ```bash
   gcloud services enable \
     pubsub.googleapis.com \
     iam.googleapis.com \
     logging.googleapis.com \
     monitoring.googleapis.com \
     container.googleapis.com
   ```

## Usage

```bash
# Authenticate with GCP
gcloud auth application-default login

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

## Workload Identity Setup

After applying, update your Helm values:

```yaml
serviceAccount:
  annotations:
    iam.gke.io/gcp-service-account: <service_account_email>
```

## Testing Pub/Sub Locally

Use the emulator for local testing:

```bash
gcloud beta emulators pubsub start --project=fraud-detection-project
export PUBSUB_EMULATOR_HOST=localhost:8085
```

## Important Notes

### Workload Identity

The service accounts are configured for Workload Identity. The annotation binding allows Kubernetes service accounts to impersonate GCP service accounts.

### Using Existing GKE Cluster

If using an existing cluster, set `create_gke_cluster = false`. Ensure Workload Identity is enabled:

```bash
gcloud container clusters update CLUSTER_NAME \
  --workload-pool=PROJECT_ID.svc.id.goog
```

### Cost Considerations

- Pub/Sub: $40 per TB for message ingestion (first 10 GB free)
- GKE Cluster: ~$75/month for control plane + node costs
- Stackdriver Logging: $0.50 per GB (first 50 GB free)
- Monitoring: Free for GKE metrics

## Monitoring and Alerts

### Log-based Metrics

- `fraud_alert_count`: Tracks fraud detection events
- Automatically extracted from structured logs

### Alert Policies

- **High Fraud Rate**: Alerts when fraud detection exceeds threshold
- **DLQ Messages**: Alerts when messages appear in dead letter queue

## Outputs

After applying:
- Pub/Sub topic and subscription names
- Service account emails for Workload Identity
- GKE cluster information (if created)

## Cleanup

```bash
terraform destroy
```

## Security Best Practices

1. Use Workload Identity instead of service account keys
2. Enable Binary Authorization for image signing
3. Use Private GKE clusters
4. Implement VPC Service Controls
5. Enable audit logging
6. Use Secret Manager for sensitive data

