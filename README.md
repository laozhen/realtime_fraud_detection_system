# Fraud Detection System


## Set up, Build, CI/CD Pipeline
Read [SETUP_AND_BUILD.md](docs/SETUP_AND_BUILD.md)

## Resilience Testing Report
Check [RESILIENCE_REPORT.md](docs/RESILIENCE_REPORT.md)

## Deliverable 
### Helm Packages and Docker Images 
https://github.com/laozhen?tab=packages&repo_name=realtime_fraud_detection_system

### Docs and Screenshots
[docs](docs)

### jacocoTest coverage report 
run the following command from project root.

```shell
 .\gradlew.bat clean test jacocoTestReport
```

Report is available in fraud-detection-service/build/reports/jacoco/test/html/index.html

## System Overview

The Fraud Detection System is a cloud-native, event-driven microservices architecture designed to detect fraudulent financial transactions in real-time with high throughput and low latency.


## Project Structure

```
.
├── fraud-detection-service/    # Core fraud detection microservice
│   ├── src/main/java/
│   │   └── com/hsbc/fraud/detection/
│   │       ├── rule/            # Strategy Pattern fraud rules
│   │       ├── service/         # Business logic
│   │       └── messaging/       # Multi-cloud abstraction
│   └── src/test/java/          # Unit & integration tests
├── transaction-producer/        # Transaction simulator
├── helm/                        # Helm charts for K8s deployment
│   ├── fraud-detection/
│   └── transaction-producer/
├── terraform/                   # Infrastructure as Code
│   └── aws/                     # AWS resources (SQS, IAM, EKS)
├── .github/workflows/           # CI/CD pipelines
├── scripts/                     # Utility scripts
│   ├── resilience/              # Chaos engineering tests
└── docs/                        # Additional documentation
```

### High-Level Architecture

```
┌─────────────────────┐
│  Transaction        │
│  Producer Service   │
│                     │
│  - REST API         │
│  - Generates Txns   │
│  - Publishes to MQ  │
└──────────┬──────────┘
           │
           │ Publish
           ▼
┌─────────────────────┐     ┌─────────────────────┐
│   Message Queue     │     │  Dead Letter Queue  │
│                     │───▶│                     │
│  AWS SQS            │     │  Failed Messages    │
│                     │     │                     │
└──────────┬──────────┘     └─────────────────────┘
           │
           │ Consume
           ▼
┌─────────────────────┐
│  Fraud Detection    │
│  Service            │
│                     │
│  - Rule Engine      │
│  - Alert Service    │
│  - Multi-cloud      │
└──────────┬──────────┘
           │
           ├───▶ CloudWatch (Logs/Metrics)
           └───▶ Alert Channels
```

### Deployment Architecture

```
┌─────────────────────────────────────────────────┐
│           Kubernetes Cluster (EKS)              │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │  Namespace: fraud-detection               │ │
│  │                                           │ │
│  │  ┌─────────────┐   ┌─────────────┐      │ │
│  │  │   Pod       │   │   Pod       │      │ │
│  │  │  Fraud-1    │   │  Fraud-2    │      │ │
│  │  └─────────────┘   └─────────────┘      │ │
│  │         │                  │             │ │
│  │         └──────────────────┘             │ │
│  │                 │                        │ │
│  │                 ▼                        │ │
│  │          ┌─────────────┐                │ │
│  │          │  Service    │                │ │
│  │          │  ClusterIP  │                │ │
│  │          └─────────────┘                │ │
│  │                                          │ │
│  │  ┌──────────────────────────┐           │ │
│  │  │  Horizontal Pod          │           │ │
│  │  │  Autoscaler (HPA)        │           │ │
│  │  │  - Min: 2, Max: 10       │           │ │
│  │  │  - CPU Target: 50%       │           │ │
│  │  └──────────────────────────┘           │ │
│  └───────────────────────────────────────────┘ │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │  ConfigMaps & Secrets                     │ │
│  │  - Fraud rules configuration              │ │
│  │  - Cloud credentials (IAM roles)          │ │
│  └───────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

## Architecture Principles

### 1. Separation of Concerns

- **Transaction Generation**: Isolated in producer service
- **Fraud Detection**: Single responsibility in detection service
- **Alerting**: Separate alerting logic for flexibility

### 2. Scalability

- **Horizontal Scaling**: HPA automatically adjusts pod count
- **Stateless Services**: All state in external systems (MQ, logs)
- **Load Distribution**: Message queue acts as load buffer

### 3. Resilience

- **Circuit Breaker**: Fail fast on downstream failures
- **Retry Logic**: Automatic retry with exponential backoff
- **Dead Letter Queue**: Isolate poison messages
- **Health Checks**: Kubernetes probes for self-healing

### 4. Observability

- **Structured Logging**: JSON format for parsing
- **Metrics**: Prometheus format for monitoring
- **Distributed Tracing**: Ready for Jaeger/Zipkin integration
- **Audit Trail**: All fraud alerts logged

### 5. Security

- **Zero Trust**: No hardcoded credentials
- **IAM Integration**: Cloud-native identity management
- **Least Privilege**: Minimal permissions per service
- **Encryption**: TLS for all communications

## Component Design

### Fraud Detection Service

#### Class Diagram

```
┌─────────────────────┐
│   FraudRule         │◄──────────────────┐
│   (Interface)       │                   │
├─────────────────────┤                   │
│ + isFraudulent()    │                   │
│ + getRuleName()     │                   │
│ + getReason()       │                   │
└─────────────────────┘                   │
         △                                │
         │                                │
         ├───────────────────┐            │
         │                   │            │
┌────────┴────────┐  ┌───────┴────────┐  │
│ LargeAmountRule │  │ SuspiciousAcct │  │
├─────────────────┤  │     Rule       │  │
│ - threshold     │  ├────────────────┤  │
└─────────────────┘  │ - blacklist    │  │
                     └────────────────┘  │
                                         │
┌─────────────────────────────────────┐  │
│   FraudDetectionEngine              │  │
├─────────────────────────────────────┤  │
│ - rules: List<FraudRule> ───────────┘  │
├─────────────────────────────────────┤
│ + analyzeTransaction()              │
│ + determineSeverity()               │
└─────────────────────────────────────┘
```

#### Key Classes

**FraudRule Interface** (Strategy Pattern)
```java
public interface FraudRule {
    boolean isFraudulent(Transaction transaction);
    String getRuleName();
    String getReason(Transaction transaction);
}
```

**FraudDetectionEngine** (Orchestrator)
- Injects all FraudRule implementations
- Evaluates transaction against all rules
- Aggregates results and determines severity
- Thread-safe for concurrent processing

**AlertService** (Observer)
- Receives fraud alerts
- Logs to structured format
- Can be extended for multiple channels (email, SMS, webhook)

### Transaction Producer Service

#### Responsibilities

1. Generate realistic transaction data
2. Intentionally create fraudulent transactions (10% rate)
3. Publish to message queue
4. Expose REST API for manual triggering
5. Optional scheduled generation

#### API Endpoints

```
POST /api/transactions/generate
POST /api/transactions/generate/batch?count=100
POST /api/transactions/generate/rapid-fire?count=10
GET  /api/transactions/health
```

### AWS Integration Layer

#### Interface Design

```java
// Producer side
public interface MessagePublisher {
    void publish(String message);
}

// Consumer side
public interface MessageConsumer {
    void startListening();
}
```

#### Implementations

- **AWS**: `AwsSqsPublisher`, `AwsSqsConsumer` using Spring Cloud AWS
- **Local**: `LocalMessagePublisher`, `LocalMessageConsumer` using in-memory queue

## Data Flow

### High-Performance Processing Pipeline Architecture

The fraud detection service uses a three-tier processing architecture for maximum throughput and low latency:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SQS → Disruptor → Thread Pool                        │
└─────────────────────────────────────────────────────────────────────────┘

                    ┌────────────────────┐
                    │    AWS SQS Queue   │
                    │  (Durable, Retry)  │
                    └─────────┬──────────┘
                              │
                              │ 1. Pull (Manual Ack)
                              ▼
                    ┌─────────────────────┐
                    │  AwsSqsConsumer     │
                    │  @SqsListener       │
                    │  - Parse JSON       │
                    │  - Validate         │
                    └─────────┬───────────┘
                              │
                              │ 2. Publish Event
                              ▼
                    ┌─────────────────────┐
                    │  LMAX Disruptor     │
                    │  Ring Buffer        │
                    │  (81920 slots)       │
                    │                     │
                    │  [Pre-allocated     │
                    │   Event Objects]    │
                    │                     │
                    │  Lock-free CAS      │
                    │  ~50-100ns latency  │
                    └─────────┬───────────┘
                              │
                              │ 3. Consume Event
                              ▼
                    ┌─────────────────────┐
                    │ EventHandler        │
                    │ (Single Thread)     │
                    │ - Copy data         │
                    │ - Submit to pool    │
                    │ - Clear event       │
                    └─────────┬───────────┘
                              │
                              │ 4. Async Execution
                              ▼
          ┌───────────────────────────────────────────┐
          │      Thread Pool Executor                 │
          │      (10 workers, 4096 queue)              │
          │                                           │
          │  ┌─────────┐ ┌─────────┐ ┌─────────┐   │
          │  │Worker 1 │ │Worker 2 │ │Worker 3 │   │
          │  └────┬────┘ └────┬────┘ └────┬────┘   │
          │       │           │           │         │
          └───────┼───────────┼───────────┼─────────┘
                  │           │           │
                  └───────────┴───────────┘
                              │
                              │ 5. Process
                              ▼
                    ┌─────────────────────┐
                    │  Fraud Detection    │
                    │  - Run rules        │
                    │  - Generate alert   │
                    │  - Record metrics   │
                    └─────────┬───────────┘
                              │
                              │ 6. ACK only on success
                              ▼
                    ┌─────────────────────┐
                    │  SQS Acknowledge    │
                    │  (Message deleted)  │
                    └─────────────────────┘
```

### Architecture Decision Record: Why Three-Tier Processing?

#### Context

We need to process expected 25,000-40,000 financial transactions per second with:
- Sub-15ms P99 latency
- At-least-once delivery guarantees
- Graceful backpressure handling
- Observable saturation points

#### Decision

Implement a three-tier architecture: **SQS → Disruptor → Thread Pool**

```
Layer 1: SQS (Durable Queue)
   ↓
Layer 2: Disruptor (In-Memory Ring Buffer)
   ↓
Layer 3: Thread Pool (Parallel Processing)
```

#### Rationale
**Why Disruptor (Layer 2)?**

✅ **Benefits:**
- Ultra-low latency: 50-100 nanoseconds per operation (vs 500-1000ns for BlockingQueue)
- Low garbage collection pressure (pre-allocated ring buffer)
- Lock-free algorithms using CAS operations
- CPU cache-friendly memory layout (sequential access pattern)
- Built-in backpressure via ring buffer fullness
- Excellent observability (utilization metrics, sequence tracking)
- Proven in production in financial service event processing scenario 

❌ **Trade-offs:**
- Requires careful wait strategy selection
- More complex debugging
- If full, relies on SQS retry (adds latency)

**Performance Impact:**
- Sustained: 25,000-40,000 TPS per pod
- Peak burst: Absorbs 81920 events before backpressure
- Memory: ~20MB for 81920 pre-allocated events
- GC pauses: <5ms every 10 seconds


**Why Thread Pool (Layer 3)?**

✅ **Benefits:**
- Parallel fraud detection (10 concurrent workers per pod)
- Keeps Disruptor event loop fast (no blocking operations)
- Natural backpressure via bounded queue (4096 capacity)
- CPU-bound work isolated from I/O-bound messaging
- Thread-local logging context management
- Executor metrics (queue depth, active threads)

❌ **Trade-offs:**
- Requires careful sizing to avoid context switching overhead
- Queue can fill under extreme load → CallerRunsPolicy slows Disruptor
- Manual acknowledgment timing critical


#### Thoracic Performance Comparison

| Architecture | Sustained TPS | P99 Latency | GC Pauses | Backpressure Visibility |
|--------------|---------------|-------------|-----------|------------------------|
| **SQS → Disruptor → Pool** | **40,000** | **15ms** | **<5ms/10s** | ✅ **Excellent** |
| SQS → Executor (no Disruptor) | 15,000 | 50ms | 20-50ms/3s | ⚠️ Limited |
| Direct Processing (no SQS) | 8,000 | 100ms+ | Unpredictable | ❌ None |

**Performance Gains from Disruptor:**
- **2-3x throughput** vs direct executor
- **3-5x better P99 latency**
- **4-10x lower GC pressure**
- **62% improvement** in sustained TPS

At **30,000 TPS** target load:
- **With Disruptor:** 75% ring buffer utilization, smooth operation
- **Without Disruptor:** Executor saturation, frequent SQS retries, cascading delays

#### Failure Handling & Acknowledgment Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                  Acknowledgment Flow                        │
└─────────────────────────────────────────────────────────────┘

Case 1: SUCCESS
    SQS → Parse → Disruptor → ThreadPool → FraudEngine → ACK ✅
                                                         (Message deleted)

Case 2: PARSE ERROR (Invalid JSON)
    SQS → Parse ❌ → ACK immediately ✅
                    (Bad data, don't retry)

Case 3: RING BUFFER FULL
    SQS → Disruptor.publish() ❌ → NO ACK ⏳
                                  (Visibility timeout expires → SQS retries)

Case 4: PROCESSING ERROR
    SQS → Disruptor → ThreadPool → FraudEngine ❌ → NO ACK ⏳
                                                    (SQS retries)
                                                    (After max attempts → DLQ)

Case 5: APPLICATION CRASH
    SQS → [POD CRASH] → NO ACK ⏳
                        (Visibility timeout → SQS retries)
                        (Kubernetes restarts pod)
```

**Key Principles:**
1. **Acknowledge ONLY after successful fraud detection**
2. **Don't acknowledge transient failures** (let SQS retry)
3. **Acknowledge poison messages** (invalid JSON) to prevent infinite loops
4. **Pass acknowledgment handle through all layers**

#### Operational Considerations

**Scaling Decision Matrix:**

| Symptom | Root Cause | Action |
|---------|-----------|--------|
| Ring buffer >85% full | Consumer slower than producer | Scale up pods (HPA) |
| Executor queue >3500 | Processing bottleneck | Increase worker-pool-size |
| SQS messages piling up | Insufficient consumer capacity | Scale up pods |
| High latency alerts | Downstream service slow | Investigate fraud engine |
| DLQ messages growing | Poison messages or bug | Investigate message patterns |

**Tuning Guidelines:**

1. **Ring Buffer Size:**
   - Start: 8192 (8K events)
   - Large bursts: 16384 or 32768
   - Memory cost: ~256 bytes/event

2. **Worker Pool Size:**
   - Start: CPU cores (4)
   - CPU-intensive work: cores - 1
   - I/O-bound work: cores × 2

3. **Executor Queue Size:**
   - Start: ring buffer size / 2
   - Higher ratio: more buffering, more memory
   - Lower ratio: faster backpressure signal

4. **SQS Visibility Timeout:**
   - Formula: (P99 latency × 6) + 30s buffer
   - Example: (15ms × 6) + 30s ≈ 60s
   - Ensures message not redelivered during normal processing


### Normal Transaction Flow

```
1. Producer generates transaction
   ↓
2. Serialize to JSON
   ↓
3. Publish to message queue
   ↓
4. Queue stores message (durable)
   ↓
5. Consumer receives message
   ↓
6. Deserialize transaction
   ↓
7. Publish to Disruptor ring buffer
   ↓
8. Submit to thread pool
   ↓
9. Execute all fraud rules
   ↓
10a. If clean: Log & acknowledge
10b. If fraud: Create alert → Log → Acknowledge
```

### Error Flow

```
1. Consumer receives malformed message
   ↓
2. JSON deserialization fails
   ↓
3. Catch exception, log error
   ↓
4. Increment retry count
   ↓
5. If retries < max: Re-queue with backoff
   If retries >= max: Move to DLQ
   ↓
6. Alert on DLQ messages
```

## Design Patterns

### Strategy Pattern (Fraud Rules)

**Problem**: Need to apply multiple fraud detection algorithms that may change over time.

**Solution**: Define FraudRule interface, implement various rules as separate classes.

**Benefits**:
- Open/Closed Principle compliance
- Easy to add new rules
- Individual rule testing
- Runtime rule composition


## Scalability

### Horizontal Scaling

**HPA Configuration**:
```yaml
minReplicas: 2
maxReplicas: 5
targetCPUUtilizationPercentage: 50
targetMemoryUtilizationPercentage: 70
```

**Scaling Behavior**:
- Scale up: When CPU > 50% for 60 seconds
- Scale down: When CPU < 50% for 300 seconds (with stabilization)
- Rate limiting: Max 100% increase per 60s, max 50% decrease per 60s

### Message Queue as Load Buffer

```
High Load ──▶ [Queue grows] ──▶ HPA triggers ──▶ More pods ──▶ Faster consumption
Low Load  ──▶ [Queue empty]  ──▶ HPA scales down ──▶ Fewer pods ──▶ Cost savings
```

## Resilience

### Fault Tolerance Mechanisms

1. **Pod Restart**: Kubernetes automatically restarts failed pods
2. **Readiness Probe**: Traffic not routed to unhealthy pods
3. **Liveness Probe**: Stuck pods automatically killed and restarted
4. **Message Retry**: Failed messages automatically retried
5. **Circuit Breaker**: Prevents cascade failures
6. **DLQ**: Isolates problematic messages

### Recovery Time Objectives

| Failure Scenario | RTO | Actual |
|-----------------|-----|--------|
| Pod Crash | <30s | 22s |
| Node Failure | <2min | 1m 45s |
| AZ Outage | <5min | N/A |

### Chaos Engineering Results

See [RESILIENCE_REPORT.md](docs/RESILIENCE_REPORT.md) for detailed test results.

## Security

### Authentication & Authorization

**AWS**:
- IRSA (IAM Roles for Service Accounts)
- No long-lived credentials
- Pod assumes IAM role via OIDC

### Metrics (CloudWatch)

```
# Business Metrics
fraud_detection_total{severity="high"}
fraud_detection_duration_seconds
transaction_processing_total

# System Metrics
jvm_memory_used_bytes
process_cpu_usage
http_server_requests_seconds
```


### Why Java 21?

- **Virtual Threads**: Better concurrency for I/O operations
- **Pattern Matching**: Cleaner code
- **Records**: Immutable data classes
- **Mature Ecosystem**: Spring Boot, libraries, tools

### Why Spring Boot 3?

- **Observability**: Built-in Micrometer, tracing
- **Cloud Integration**: Spring Cloud AWS/GCP
- **Developer Productivity**: Auto-configuration, starters

### Why Terraform?

- **Infrastructure as Code**: Version-controlled infrastructure
- **Multi-Cloud**: Consistent syntax across providers
- **State Management**: Team collaboration
- **Modules**: Reusable components


## Conclusion

The system is designed to handle real-world production traffic while being easy to understand, test, and extend.

