# Metrics Monitoring and Alerting System

A complete metrics monitoring system that collects, stores, and visualizes HTTP service metrics. Features a custom time-series database with PromQL support, Kafka-based pipeline, and Grafana dashboards.

## Architecture

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
              Kafka Consumer (2 replicas)
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

See `docs/components.drawio` for the full component diagram.

## Components

| Component | Port | Description |
|-----------|------|-------------|
| TSDB Server | 8080 | Custom time-series database with PromQL, Prometheus HTTP API |
| Load Client | 8081 | Generates HTTP load, exposes Prometheus metrics |
| Metrics Collector | 8082 | Scrapes /metrics endpoints, publishes to Kafka |
| Kafka Consumer | 8083 | Reads from Kafka, writes to TSDB in batches |
| Kafka | 9092 | Message broker (KRaft mode, no ZooKeeper) |
| Redis | 6379 | Query result cache |
| Grafana | 3000 | Visualization dashboards |

## Tech Stack

- Java 21 (Virtual Threads)
- Spring Boot 3.4.x
- Gradle (Groovy DSL)
- Lombok
- ANTLR4 (PromQL parser)
- Apache Kafka 3.7 (KRaft)
- Redis 7
- Grafana 11
- Docker Compose

## Prerequisites

- Java 21+
- Docker and Docker Compose

## Quick Start

Start all core services:

```bash
docker compose up -d --build
```

Start with load testing client:

```bash
docker compose --profile test up -d --build
```

Access Grafana at http://localhost:3000 (admin/admin).

## Building

```bash
./gradlew build
```

Run tests:

```bash
./gradlew test
```

## TSDB API

The TSDB server exposes a Prometheus-compatible HTTP API:

```bash
# Instant query
curl 'http://localhost:8080/api/v1/query?query=http_requests_total'

# Range query
curl 'http://localhost:8080/api/v1/query_range?query=rate(http_requests_total[1m])&start=1713052800&end=1713056400&step=15s'

# Label values
curl 'http://localhost:8080/api/v1/label/method/values'

# Write metrics
curl -X POST http://localhost:8080/api/v1/write \
  -H 'Content-Type: application/json' \
  -d '[{"name":"test_metric","labels":{"env":"prod"},"value":42.0,"timestamp":1713052800000}]'
```

## Supported PromQL

- Instant and range vector selectors: `metric_name{label="value"}[5m]`
- Label matchers: `=`, `!=`, `=~`, `!~`
- Functions: `rate()`, `irate()`, `increase()`
- Aggregations: `sum`, `avg`, `min`, `max`, `count` with `by`/`without`
- Binary arithmetic: `+`, `-`, `*`, `/`
- Parentheses for grouping

## Grafana Dashboard

The auto-provisioned dashboard includes:

- Request Rate (req/s by method and status)
- Error Rate (errors/s)
- Latency P95 and P99
- Requests In Flight (gauge)
- Throughput (total req/s)
- Status Code Distribution (pie chart)

## High Availability

- **Kafka**: 3 partitions for `metrics-raw` topic
- **Metrics Collector**: Stateless, horizontally scalable
- **Kafka Consumer**: Consumer group with auto-rebalancing (2 replicas by default)
- **TSDB Server**: File-backed storage survives restarts
- **Redis**: Single instance (upgradeable to Sentinel/Cluster)
