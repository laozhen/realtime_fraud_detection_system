#!/bin/bash

# Chaos Engineering Test Script for Fraud Detection System
# This script simulates various failure scenarios and monitors system recovery

set -e

NAMESPACE="${NAMESPACE:-fraud-detection}"
DURATION="${DURATION:-300}"  # 5 minutes
OUTPUT_DIR="./resilience-test-results"

echo "================================================"
echo "Fraud Detection System - Chaos Engineering Test"
echo "================================================"
echo "Namespace: $NAMESPACE"
echo "Test Duration: ${DURATION}s"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="$OUTPUT_DIR/chaos-test-${TIMESTAMP}.log"

# Log function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$REPORT_FILE"
}

# Check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."
    
    if ! command -v kubectl &> /dev/null; then
        log "ERROR: kubectl not found"
        exit 1
    fi
    
    if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
        log "ERROR: Namespace $NAMESPACE not found"
        exit 1
    fi
    
    log "Prerequisites check passed"
}

# Baseline metrics
capture_baseline() {
    log "Capturing baseline metrics..."
    
    kubectl get pods -n "$NAMESPACE" > "$OUTPUT_DIR/baseline-pods-${TIMESTAMP}.txt"
    kubectl top pods -n "$NAMESPACE" > "$OUTPUT_DIR/baseline-resources-${TIMESTAMP}.txt" || true
    kubectl get hpa -n "$NAMESPACE" > "$OUTPUT_DIR/baseline-hpa-${TIMESTAMP}.txt"
    
    log "Baseline captured"
}

# Test 1: Random pod deletion
test_pod_deletion() {
    log ""
    log "=== TEST 1: Random Pod Deletion ==="
    log "Simulating pod failures by randomly deleting pods"
    
    local test_start=$(date +%s)
    local test_end=$((test_start + DURATION))
    local deletion_count=0
    
    while [ $(date +%s) -lt $test_end ]; do
        # Get fraud-detection pods
        PODS=$(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=fraud-detection --no-headers -o custom-columns=":metadata.name")
        
        if [ -n "$PODS" ]; then
            # Select random pod
            POD_TO_DELETE=$(echo "$PODS" | shuf -n 1)
            
            log "Deleting pod: $POD_TO_DELETE"
            kubectl delete pod "$POD_TO_DELETE" -n "$NAMESPACE" --grace-period=0 --force &
            
            deletion_count=$((deletion_count + 1))
            
            # Wait 30 seconds before next deletion
            sleep 30
            
            # Check pod recovery
            kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=fraud-detection \
                -n "$NAMESPACE" --timeout=60s || log "WARNING: Pods not ready after deletion"
            
            # Capture current state
            kubectl get pods -n "$NAMESPACE" >> "$OUTPUT_DIR/pod-deletion-state-${TIMESTAMP}.log"
        fi
    done
    
    log "Test 1 completed. Total deletions: $deletion_count"
}

# Test 2: Node drain simulation
test_node_pressure() {
    log ""
    log "=== TEST 2: Resource Pressure Test ==="
    log "Monitoring HPA behavior under load"
    
    local test_start=$(date +%s)
    local test_end=$((test_start + 120))  # 2 minutes
    
    # Get transaction producer pod
    PRODUCER_POD=$(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=transaction-producer \
        --no-headers -o custom-columns=":metadata.name" | head -n 1)
    
    if [ -n "$PRODUCER_POD" ]; then
        log "Generating high transaction load..."
        
        # Generate burst of transactions
        for i in {1..10}; do
            kubectl exec -n "$NAMESPACE" "$PRODUCER_POD" -- \
                curl -s -X POST http://localhost:8081/api/transactions/generate/batch?count=100 &
        done
        
        # Monitor HPA scaling
        while [ $(date +%s) -lt $test_end ]; do
            kubectl get hpa -n "$NAMESPACE" | tee -a "$OUTPUT_DIR/hpa-scaling-${TIMESTAMP}.log"
            kubectl top pods -n "$NAMESPACE" | tee -a "$OUTPUT_DIR/resource-usage-${TIMESTAMP}.log" || true
            sleep 10
        done
    else
        log "WARNING: Transaction producer pod not found, skipping load test"
    fi
    
    log "Test 2 completed"
}

# Test 3: Network partition simulation
test_network_issues() {
    log ""
    log "=== TEST 3: Service Availability Test ==="
    log "Testing service endpoints during pod churn"
    
    local success_count=0
    local failure_count=0
    local test_duration=60
    
    for i in $(seq 1 $test_duration); do
        # Test fraud detection service health
        if kubectl run curl-test-$i --image=curlimages/curl:latest --rm -i --restart=Never \
            -n "$NAMESPACE" -- curl -s -f http://fraud-detection:8080/actuator/health &> /dev/null; then
            success_count=$((success_count + 1))
        else
            failure_count=$((failure_count + 1))
        fi
        
        sleep 1
    done
    
    local success_rate=$((success_count * 100 / test_duration))
    
    log "Test 3 completed"
    log "Success Rate: ${success_rate}% (${success_count}/${test_duration})"
    log "Failures: $failure_count"
}

# Test 4: Recovery time measurement
test_recovery_time() {
    log ""
    log "=== TEST 4: Recovery Time Measurement ==="
    
    # Get a pod to delete
    POD=$(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=fraud-detection \
        --no-headers -o custom-columns=":metadata.name" | head -n 1)
    
    if [ -n "$POD" ]; then
        local delete_time=$(date +%s)
        log "Deleting pod: $POD"
        kubectl delete pod "$POD" -n "$NAMESPACE" --grace-period=0 --force
        
        # Wait for new pod to be ready
        log "Waiting for replacement pod..."
        kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=fraud-detection \
            -n "$NAMESPACE" --timeout=120s
        
        local ready_time=$(date +%s)
        local recovery_time=$((ready_time - delete_time))
        
        log "Recovery time: ${recovery_time}s"
        echo "RECOVERY_TIME_SECONDS=$recovery_time" >> "$OUTPUT_DIR/metrics-${TIMESTAMP}.txt"
    fi
    
    log "Test 4 completed"
}

# Final state capture
capture_final_state() {
    log ""
    log "Capturing final state..."
    
    kubectl get pods -n "$NAMESPACE" -o wide > "$OUTPUT_DIR/final-pods-${TIMESTAMP}.txt"
    kubectl get events -n "$NAMESPACE" --sort-by='.lastTimestamp' > "$OUTPUT_DIR/events-${TIMESTAMP}.txt"
    kubectl describe hpa -n "$NAMESPACE" > "$OUTPUT_DIR/final-hpa-${TIMESTAMP}.txt"
    kubectl top pods -n "$NAMESPACE" > "$OUTPUT_DIR/final-resources-${TIMESTAMP}.txt" || true
    
    # Get logs from key pods
    for pod in $(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=fraud-detection --no-headers -o custom-columns=":metadata.name"); do
        kubectl logs "$pod" -n "$NAMESPACE" --tail=100 > "$OUTPUT_DIR/logs-${pod}-${TIMESTAMP}.txt" 2>&1 || true
    done
    
    log "Final state captured"
}

# Generate report
generate_report() {
    log ""
    log "=== RESILIENCE TEST SUMMARY ==="
    
    local report="$OUTPUT_DIR/SUMMARY-${TIMESTAMP}.txt"
    
    {
        echo "Fraud Detection System - Resilience Test Report"
        echo "================================================"
        echo "Test Date: $(date)"
        echo "Namespace: $NAMESPACE"
        echo "Duration: ${DURATION}s"
        echo ""
        echo "Test Results:"
        echo "------------"
        grep "completed" "$REPORT_FILE"
        echo ""
        echo "Metrics:"
        echo "-------"
        cat "$OUTPUT_DIR/metrics-${TIMESTAMP}.txt" 2>/dev/null || echo "No metrics captured"
        echo ""
        echo "Final Pod Status:"
        echo "----------------"
        cat "$OUTPUT_DIR/final-pods-${TIMESTAMP}.txt"
    } > "$report"
    
    log "Report generated: $report"
    cat "$report"
}

# Main execution
main() {
    check_prerequisites
    capture_baseline
    
    # Run all tests
    test_pod_deletion
    test_node_pressure
    test_network_issues
    test_recovery_time
    
    capture_final_state
    generate_report
    
    log ""
    log "================================================"
    log "Chaos test completed successfully!"
    log "Results saved to: $OUTPUT_DIR"
    log "================================================"
}

# Run main
main

