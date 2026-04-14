# Metrics Monitoring and Alerting System — Design Spec

## Overview

A metrics monitoring system that collects, stores, and visualizes HTTP service metrics. Built with Java 21, Spring Boot 3.x, Groovy Gradle, and Lombok. All components run via Docker Compose.

## System Architecture

```
Load Client ──▶ /metrics (Prometheus format)
                     │
          Metrics Collector (scrapes /metrics)
                     │
                     ▼
              Kafka (KRaft mode)
           topic: metrics-raw
                     │
                     ▼
              Kafka Consumer
                     │
                     ▼
         TSDB Server (write API)
           ┌────────┴────────┐
       Columnar        Prometheus HTTP API
       Storage              │
                        Redis Cache
                             │
                          Grafana
```

**Flow**: Load Client generates HTTP traffic and exposes metrics → Collector scrapes and publishes to Kafka → Consumer reads and writes to TSDB → Grafana queries TSDB via Prometheus API with Redis caching.

## Project Structure

Gradle multi-module monorepo:

```
metrics-collecting-system/
├── build.gradle                 # Root build: shared deps, Java 21, Lombok
├── settings.gradle              # Module includes
├── docker-compose.yml
├── common/                      # Shared DTOs, Kafka config, Prometheus data model
│   └── build.gradle
├── tsdb-core/                   # Storage engine, PromQL parser/evaluator
│   └── build.gradle
├── tsdb-server/                 # Spring Boot: Prometheus HTTP API, write API, Redis cache
│   ├── build.gradle
│   └── Dockerfile
├── metrics-collector/           # Spring Boot: scrapes /metrics, publishes to Kafka
│   ├── build.gradle
│   └── Dockerfile
├── kafka-consumer/              # Spring Boot: reads Kafka, writes to TSDB
│   ├── build.gradle
│   └── Dockerfile
├── load-client/                 # Spring Boot: generates load, exposes /metrics
│   ├── build.gradle
│   └── Dockerfile
└── grafana/
    └── provisioning/            # Auto-provisioned datasource + dashboards
```

## Component Details

### 1. Common Module (`common`)

Shared library, not a standalone service.

- **`MetricSample`**: DTO — metric name, labels (Map<String, String>), value (double), timestamp (long ms)
- **`PrometheusTextParser`**: Parses Prometheus exposition format text into `MetricSample` list
- **Kafka serialization**: JSON serializer/deserializer for `MetricSample`
- **Constants**: Topic names, default configs

### 2. TSDB Core (`tsdb-core`)

The storage and query engine. No Spring dependency — pure Java library.

#### Storage Engine

- **Series index**: `ConcurrentHashMap<SeriesKey, SeriesId>` where `SeriesKey` = metric name + sorted labels
- **Columnar data**: Per series — `long[] timestamps` and `double[] values` arrays in time-sorted order
- **Time partitions**: 1-hour blocks. Active partition in memory, completed partitions flushed to disk as binary files
- **Disk format**: Simple binary — header (seriesId, count), then packed longs (timestamps), then packed doubles (values)
- **Retention**: Configurable (default 24h). Background thread deletes expired partitions
- **Write path**: `void write(String metricName, Map<String, String> labels, double value, long timestamp)`
- **Read path**: `List<Sample> query(String metricName, Map<String, String> matchers, long startMs, long endMs)`

#### PromQL Parser (ANTLR4)

Supported grammar subset:

```
expression     : binaryExpr | aggregation | functionCall | vectorSelector | '(' expression ')'
binaryExpr     : expression ('+' | '-' | '*' | '/') expression
vectorSelector : metricName labelMatchers? ('[' duration ']')?
labelMatchers  : '{' matcher (',' matcher)* '}'
matcher        : labelName ('=' | '!=' | '=~' | '!~') STRING
aggregation    : aggOp (('by' | 'without') '(' labelList ')')? '(' expression ')'
               | aggOp '(' expression ')' (('by' | 'without') '(' labelList ')')?
aggOp          : 'sum' | 'avg' | 'min' | 'max' | 'count'
functionCall   : ('rate' | 'irate' | 'increase') '(' expression ')'
duration       : NUMBER ('s' | 'm' | 'h' | 'd')
```

#### PromQL Evaluator

- Walks AST from parser
- `VectorSelector` → fetch from storage, apply label matchers (regex via `java.util.regex`)
- `rate()` → per-second rate over range vector using first/last samples
- `irate()` → instantaneous rate using last two samples
- `increase()` → total increase over range
- Aggregations → group by labels, apply aggregate function
- Binary expressions → evaluate both sides, match series by labels, apply arithmetic
- Returns `QueryResult` with `ResultType` (vector/matrix) and data

### 3. TSDB Server (`tsdb-server`)

Spring Boot application exposing the TSDB as a service.

#### Prometheus HTTP API

- `GET /api/v1/query?query={promql}&time={timestamp}` — instant query
- `GET /api/v1/query_range?query={promql}&start={ts}&end={ts}&step={duration}` — range query
- `GET /api/v1/label/{name}/values` — label value autocomplete
- `GET /api/v1/series?match[]={selector}` — series metadata
- Response format: Prometheus JSON API format (`{"status":"success","data":{...}}`)

#### Write API (Internal)

- `POST /api/v1/write` — accepts JSON array of `MetricSample`, writes to storage engine
- Not Prometheus remote write protobuf — simplified JSON for internal use

#### Redis Cache

- Spring Data Redis
- Cache key: `SHA-256(query + alignedTimeRange + step)`
- Time alignment: round start/end to step boundaries for cache hit rate
- TTL strategy:
  - Queries touching data < 5 min old → TTL 30s
  - Queries touching only older data → TTL 5min
- Cache-aside pattern: check cache → on miss, query engine → store result → return

#### Configuration

```yaml
tsdb:
  storage:
    data-dir: /data/tsdb
    partition-duration: 1h
    retention: 24h
  cache:
    enabled: true
    default-ttl: 30s
server:
  port: 8080
spring:
  data:
    redis:
      host: redis
      port: 6379
```

### 4. Metrics Collector (`metrics-collector`)

Spring Boot application that scrapes Prometheus endpoints and publishes to Kafka.

- **Target registry**: Configurable list of scrape targets (URL + scrape interval)
- **Scrape loop**: Scheduled thread pool, one thread per target. Uses `HttpClient` to GET `/metrics`
- **Parsing**: Uses `PrometheusTextParser` from `common` to parse exposition format
- **Publishing**: Spring Kafka `KafkaTemplate<String, MetricSample>`. Key = metric name (for partition locality)
- **Self-metrics**: Exposes own `/metrics` (scrape duration, error count) via Micrometer

#### Configuration

```yaml
collector:
  targets:
    - url: http://load-client:8081/metrics
      interval: 15s
    - url: http://tsdb-server:8080/metrics
      interval: 15s
spring:
  kafka:
    bootstrap-servers: kafka:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
server:
  port: 8082
```

### 5. Kafka Consumer (`kafka-consumer`)

Spring Boot application consuming from Kafka and writing to TSDB.

- **Consumer group**: `metrics-consumers` — supports multiple instances with auto-rebalancing
- **Batch accumulation**: Accumulates samples in memory, flushes to TSDB write API either every 500 samples or every 5 seconds (whichever comes first)
- **TSDB client**: `WebClient` calling `POST tsdb-server:8080/api/v1/write`
- **Error handling**: Retry with exponential backoff (1s, 2s, 4s, max 30s). On persistent failure, log and skip (dead-letter topic optional)
- **Self-metrics**: Exposed via Micrometer (messages consumed, write latency, errors)

#### Configuration

```yaml
consumer:
  tsdb:
    write-url: http://tsdb-server:8080/api/v1/write
    batch-size: 500
    flush-interval: 5s
    retry:
      max-attempts: 5
      initial-backoff: 1s
spring:
  kafka:
    bootstrap-servers: kafka:9092
    consumer:
      group-id: metrics-consumers
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
server:
  port: 8083
```

### 6. Load Client (`load-client`)

Spring Boot application generating HTTP load and exposing metrics.

- **Mock server**: Embedded HTTP endpoint (`/api/test`) that responds with configurable latency (normal distribution, mean 100ms, stddev 30ms) and configurable error rate (5% 500s)
- **Load generator**: Configurable concurrent virtual threads (Java 21), target RPS, duration
- **Metrics exposed at `/metrics`** (Prometheus text format, self-computed — not Micrometer, to demonstrate the format):
  - `http_requests_total{method="GET", status="200"}` — counter
  - `http_requests_total{method="GET", status="500"}` — counter
  - `http_request_duration_seconds{method="GET", quantile="0.5"}` — summary
  - `http_request_duration_seconds{method="GET", quantile="0.95"}` — summary
  - `http_request_duration_seconds{method="GET", quantile="0.99"}` — summary
  - `http_request_duration_seconds_sum{method="GET"}` — counter
  - `http_request_duration_seconds_count{method="GET"}` — counter
  - `http_request_errors_total{method="GET"}` — counter
  - `http_requests_in_flight` — gauge
- **Docker profile**: `test` — started via `docker compose --profile test up load-client`

#### Configuration

```yaml
loadclient:
  target-url: http://localhost:8081/api/test
  concurrency: 10
  target-rps: 100
  mock-server:
    port: 8081
    latency-mean-ms: 100
    latency-stddev-ms: 30
    error-rate: 0.05
server:
  port: 8081
```

### 7. Grafana

Auto-provisioned via files in `grafana/provisioning/`.

#### Datasource (`datasources.yml`)

```yaml
datasources:
  - name: TSDB
    type: prometheus
    url: http://tsdb-server:8080
    access: proxy
    isDefault: true
```

#### Dashboard

JSON-provisioned dashboard with panels:
- **Request Rate**: `rate(http_requests_total[1m])` — graph
- **Error Rate**: `rate(http_request_errors_total[1m]) / rate(http_requests_total[1m])` — graph
- **Latency P95**: `http_request_duration_seconds{quantile="0.95"}` — graph
- **Latency P99**: `http_request_duration_seconds{quantile="0.99"}` — graph
- **Requests In Flight**: `http_requests_in_flight` — gauge
- **Throughput**: `sum(rate(http_requests_total[1m]))` — stat panel
- **Status Code Distribution**: `sum by (status)(rate(http_requests_total[1m]))` — pie chart

### 8. Docker Compose

```yaml
services:
  kafka:
    image: apache/kafka:3.7.0
    # KRaft mode (no ZooKeeper)
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      # ...
    ports: ["9092:9092"]
    healthcheck: ...

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    healthcheck: ...

  tsdb-server:
    build: ./tsdb-server
    ports: ["8080:8080"]
    depends_on: [redis]
    volumes: [tsdb-data:/data/tsdb]
    healthcheck: ...

  metrics-collector:
    build: ./metrics-collector
    depends_on: [kafka, tsdb-server]
    deploy:
      replicas: 1

  kafka-consumer:
    build: ./kafka-consumer
    depends_on: [kafka, tsdb-server]
    deploy:
      replicas: 2

  grafana:
    image: grafana/grafana:11.0.0
    ports: ["3000:3000"]
    depends_on: [tsdb-server]
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning

  load-client:
    build: ./load-client
    profiles: [test]
    depends_on: [metrics-collector]

volumes:
  tsdb-data:
```

### 9. HA & Scalability

- **Kafka**: Handles message durability. `metrics-raw` topic: 3 partitions. Collector keys messages by metric name for partition affinity
- **Metrics Collector**: Stateless. Scale by adding instances and partitioning target list
- **Kafka Consumer**: Consumer group `metrics-consumers`. Scale by adding instances (up to partition count). Auto-rebalance on join/leave
- **TSDB Server**: Single instance for prototype. File-backed storage survives restarts. Future: sharding by metric name hash
- **Redis**: Single instance. Future: Redis Sentinel or Cluster

### 10. Testing Strategy

**Unit Tests** (JUnit 5 + Mockito):
- `tsdb-core`: Storage engine CRUD, time partitioning, retention cleanup
- `tsdb-core`: PromQL parser — valid queries, syntax errors, edge cases
- `tsdb-core`: PromQL evaluator — rate(), aggregations, label matching
- `common`: PrometheusTextParser — valid/invalid exposition format
- `metrics-collector`: Scrape scheduling, Kafka publishing
- `kafka-consumer`: Batch accumulation, retry logic

**Integration Tests** (Testcontainers):
- End-to-end: produce metrics → Kafka → consumer → TSDB → query API
- TSDB + Redis cache: verify cache hits/misses
- Grafana datasource connectivity (optional)

**Load Test Verification**:
1. `docker compose --profile test up -d`
2. Wait for load-client to generate traffic (~60s)
3. Query TSDB API: verify `http_requests_total` series exists with expected labels
4. Query Grafana API: verify dashboard panels return data
5. Verify cache hits in Redis

### 11. Component Diagram

Generated as `.drawio` XML file showing all components, their connections (HTTP, Kafka), and data flow direction.

## Key Technology Choices

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| Language | Java 21 | Virtual threads for load client concurrency |
| Framework | Spring Boot 3.x | Kafka, Redis, Web integration out of the box |
| Build | Gradle (Groovy) | Multi-module support, per user requirement |
| Boilerplate | Lombok | Per user requirement |
| Parser | ANTLR4 | Industry standard for DSL parsing, generates clean AST |
| Kafka | Apache Kafka 3.7 (KRaft) | No ZooKeeper dependency, simpler Docker setup |
| Cache | Redis 7 | TTL-based eviction, widely supported |
| Monitoring | Grafana 11 | Native Prometheus datasource, JSON-provisioned dashboards |
| Testing | JUnit 5 + Testcontainers | Standard Spring Boot testing stack |
| Containers | Docker multi-stage builds | Small runtime images (Eclipse Temurin JRE 21) |
