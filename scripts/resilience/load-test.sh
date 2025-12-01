#!/bin/bash

# Load Testing Script for Fraud Detection System
# Uses K6 for load testing transaction processing

set -e

NAMESPACE="${NAMESPACE:-fraud-detection}"
TRANSACTIONS_PER_SECOND="${TPS:-10}"
DURATION="${DURATION:-300}"
OUTPUT_DIR="./resilience-test-results"

echo "========================================="
echo "Fraud Detection System - Load Test"
echo "========================================="
echo "Target TPS: $TRANSACTIONS_PER_SECOND"
echo "Duration: ${DURATION}s"
echo ""

mkdir -p "$OUTPUT_DIR"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Get transaction producer service URL
get_service_url() {
    kubectl get svc -n "$NAMESPACE" transaction-producer -o jsonpath='{.spec.clusterIP}'
}

# Create K6 load test script
create_k6_script() {
    cat > "$OUTPUT_DIR/load-test.js" <<'EOF'
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const failureRate = new Rate('failures');

export let options = {
    stages: [
        { duration: '60s', target: __ENV.TARGET_TPS },     // Ramp up
        { duration: __ENV.DURATION + 's', target: __ENV.TARGET_TPS },  // Steady state
        { duration: '60s', target: 0 },                     // Ramp down
    ],
    thresholds: {
        'http_req_duration': ['p(95)<500'],  // 95% of requests under 500ms
        'failures': ['rate<0.1'],             // Less than 10% failure rate
    },
};

export default function() {
    // Generate transaction
    const url = `http://${__ENV.SERVICE_URL}:8081/api/transactions/generate`;
    
    const res = http.post(url, null, {
        headers: { 'Content-Type': 'application/json' },
        timeout: '30s',
    });
    
    const success = check(res, {
        'status is 200': (r) => r.status === 200,
        'transaction generated': (r) => r.json('transaction') !== undefined,
    });
    
    failureRate.add(!success);
    
    sleep(1 / __ENV.TARGET_TPS);
}
EOF
}

# Run load test with K6
run_k6_test() {
    SERVICE_URL=$(get_service_url)
    
    if [ -z "$SERVICE_URL" ]; then
        echo "ERROR: Could not get transaction-producer service URL"
        exit 1
    fi
    
    echo "Service URL: $SERVICE_URL"
    echo "Running K6 load test..."
    
    # Run K6 in a pod
    kubectl run k6-load-test -n "$NAMESPACE" \
        --image=grafana/k6:latest \
        --rm -i --restart=Never \
        --env="SERVICE_URL=$SERVICE_URL" \
        --env="TARGET_TPS=$TRANSACTIONS_PER_SECOND" \
        --env="DURATION=$DURATION" \
        -- run - < "$OUTPUT_DIR/load-test.js" \
        | tee "$OUTPUT_DIR/k6-results-${TIMESTAMP}.txt"
}

# Simple bash-based load test (fallback)
run_simple_load_test() {
    echo "Running simple load test..."
    
    SERVICE_URL=$(get_service_url)
    POD=$(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=transaction-producer \
        --no-headers -o custom-columns=":metadata.name" | head -n 1)
    
    if [ -z "$POD" ]; then
        echo "ERROR: Transaction producer pod not found"
        exit 1
    fi
    
    local start_time=$(date +%s)
    local end_time=$((start_time + DURATION))
    local success_count=0
    local failure_count=0
    local total_requests=0
    
    echo "Generating transactions for ${DURATION}s..."
    
    while [ $(date +%s) -lt $end_time ]; do
        for i in $(seq 1 $TRANSACTIONS_PER_SECOND); do
            if kubectl exec -n "$NAMESPACE" "$POD" -- \
                curl -s -X POST http://localhost:8081/api/transactions/generate \
                > /dev/null 2>&1; then
                success_count=$((success_count + 1))
            else
                failure_count=$((failure_count + 1))
            fi
            total_requests=$((total_requests + 1))
        done
        
        # Log progress every 10 seconds
        if [ $((total_requests % 100)) -eq 0 ]; then
            echo "Progress: $total_requests transactions sent (Success: $success_count, Failures: $failure_count)"
        fi
        
        sleep 1
    done
    
    local actual_duration=$(($(date +%s) - start_time))
    local actual_tps=$((total_requests / actual_duration))
    local success_rate=$((success_count * 100 / total_requests))
    
    # Save results
    cat > "$OUTPUT_DIR/load-test-results-${TIMESTAMP}.txt" <<EOF
Load Test Results
==================
Duration: ${actual_duration}s
Total Requests: $total_requests
Successful: $success_count
Failed: $failure_count
Success Rate: ${success_rate}%
Actual TPS: $actual_tps
Target TPS: $TRANSACTIONS_PER_SECOND
EOF
    
    echo ""
    cat "$OUTPUT_DIR/load-test-results-${TIMESTAMP}.txt"
}

# Monitor system during load test
monitor_system() {
    local monitor_file="$OUTPUT_DIR/system-monitor-${TIMESTAMP}.log"
    
    echo "Starting system monitoring..."
    
    while true; do
        {
            echo "=== $(date) ==="
            kubectl top pods -n "$NAMESPACE" 2>/dev/null || true
            kubectl get hpa -n "$NAMESPACE"
            echo ""
        } >> "$monitor_file"
        
        sleep 10
    done &
    
    MONITOR_PID=$!
    echo "Monitor PID: $MONITOR_PID"
}

# Cleanup
cleanup() {
    if [ -n "$MONITOR_PID" ]; then
        kill "$MONITOR_PID" 2>/dev/null || true
    fi
}

trap cleanup EXIT

# Main execution
main() {
    monitor_system
    
    # Try K6 first, fallback to simple test
    if command -v k6 &> /dev/null || kubectl get pod k6-load-test -n "$NAMESPACE" &> /dev/null; then
        create_k6_script
        run_k6_test || run_simple_load_test
    else
        echo "K6 not available, using simple load test"
        run_simple_load_test
    fi
    
    echo ""
    echo "Load test completed!"
    echo "Results saved to: $OUTPUT_DIR"
}

main

