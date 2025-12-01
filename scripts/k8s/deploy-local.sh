#!/bin/bash

# Local Kubernetes deployment script using kind or minikube

set -e

CLUSTER_NAME="${CLUSTER_NAME:-fraud-detection-local}"
USE_KIND="${USE_KIND:-true}"

echo "========================================"
echo "Local Kubernetes Deployment"
echo "========================================"
echo "Cluster: $CLUSTER_NAME"
echo "Using: $([ "$USE_KIND" = "true" ] && echo "kind" || echo "minikube")"
echo ""

# Check prerequisites
check_prerequisites() {
    echo "Checking prerequisites..."
    
    if [ "$USE_KIND" = "true" ]; then
        if ! command -v kind &> /dev/null; then
            echo "ERROR: kind not found. Install from https://kind.sigs.k8s.io/"
            exit 1
        fi
    else
        if ! command -v minikube &> /dev/null; then
            echo "ERROR: minikube not found. Install from https://minikube.sigs.k8s.io/"
            exit 1
        fi
    fi
    
    if ! command -v kubectl &> /dev/null; then
        echo "ERROR: kubectl not found"
        exit 1
    fi
    
    if ! command -v helm &> /dev/null; then
        echo "ERROR: helm not found"
        exit 1
    fi
    
    echo "Prerequisites check passed"
}

# Create local cluster
create_cluster() {
    echo "Creating local Kubernetes cluster..."
    
    if [ "$USE_KIND" = "true" ]; then
        # Create kind cluster with port mappings
        cat <<EOF | kind create cluster --name "$CLUSTER_NAME" --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  kubeadmConfigPatches:
  - |
    kind: InitConfiguration
    nodeRegistration:
      kubeletExtraArgs:
        node-labels: "ingress-ready=true"
  extraPortMappings:
  - containerPort: 30080
    hostPort: 8080
    protocol: TCP
  - containerPort: 30081
    hostPort: 8081
    protocol: TCP
- role: worker
- role: worker
EOF
    else
        minikube start --profile "$CLUSTER_NAME" --nodes 2 --cpus 4 --memory 8192
    fi
    
    echo "Cluster created successfully"
}

# Install metrics server
install_metrics_server() {
    echo "Installing metrics-server..."
    
    kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
    
    # Patch metrics-server for local development
    kubectl patch deployment metrics-server -n kube-system --type='json' \
        -p='[{"op": "add", "path": "/spec/template/spec/containers/0/args/-", "value": "--kubelet-insecure-tls"}]' || true
    
    echo "Waiting for metrics-server to be ready..."
    kubectl wait --for=condition=ready pod -l k8s-app=metrics-server -n kube-system --timeout=120s || true
}

# Build and load images
build_and_load_images() {
    echo "Building Docker images..."
    
    cd ../..
    ./gradlew fraud-detection-service:jibDockerBuild --no-daemon
    ./gradlew transaction-producer:jibDockerBuild --no-daemon
    
    if [ "$USE_KIND" = "true" ]; then
        echo "Loading images into kind..."
        kind load docker-image ghcr.io/hsbc/fraud-detection-service:1.0.0 --name "$CLUSTER_NAME"
        kind load docker-image ghcr.io/hsbc/transaction-producer:1.0.0 --name "$CLUSTER_NAME"
    fi
}

# Deploy with Helm
deploy_services() {
    echo "Deploying services with Helm..."
    
    kubectl create namespace fraud-detection --dry-run=client -o yaml | kubectl apply -f -
    
    # Deploy fraud-detection-service
    helm upgrade --install fraud-detection ./helm/fraud-detection \
        --namespace fraud-detection \
        --set cloudProvider=local \
        --set image.pullPolicy=IfNotPresent \
        --set autoscaling.enabled=true \
        --set autoscaling.minReplicas=1 \
        --set autoscaling.maxReplicas=3 \
        --wait
    
    # Deploy transaction-producer
    helm upgrade --install transaction-producer ./helm/transaction-producer \
        --namespace fraud-detection \
        --set cloudProvider=local \
        --set image.pullPolicy=IfNotPresent \
        --set service.type=NodePort \
        --set service.nodePort=30081 \
        --wait
    
    echo "Services deployed successfully"
}

# Display access information
show_access_info() {
    echo ""
    echo "========================================"
    echo "Deployment Complete!"
    echo "========================================"
    echo ""
    echo "Access Services:"
    echo "----------------"
    
    if [ "$USE_KIND" = "true" ]; then
        echo "Transaction Producer: http://localhost:8081"
        echo "  - Generate transaction: curl -X POST http://localhost:8081/api/transactions/generate"
        echo "  - Health check: curl http://localhost:8081/actuator/health"
    else
        echo "Use 'minikube service list -p $CLUSTER_NAME' to get URLs"
    fi
    
    echo ""
    echo "kubectl Commands:"
    echo "----------------"
    echo "  kubectl get pods -n fraud-detection"
    echo "  kubectl logs -f -l app.kubernetes.io/name=fraud-detection -n fraud-detection"
    echo "  kubectl port-forward svc/fraud-detection 8080:8080 -n fraud-detection"
    echo ""
    echo "To delete the cluster:"
    if [ "$USE_KIND" = "true" ]; then
        echo "  kind delete cluster --name $CLUSTER_NAME"
    else
        echo "  minikube delete --profile $CLUSTER_NAME"
    fi
}

# Main execution
main() {
    check_prerequisites
    create_cluster
    install_metrics_server
    build_and_load_images
    deploy_services
    show_access_info
}

main

