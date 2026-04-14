package com.metrics.tsdb.query;

import com.metrics.tsdb.storage.DefaultMetricStore;
import com.metrics.tsdb.storage.MetricStoreConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QueryEngineTest {

    private QueryEngine engine;
    private DefaultMetricStore store;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        MetricStoreConfig config = MetricStoreConfig.builder()
                .dataDirectory(tempDir)
                .partitionDuration(Duration.ofHours(1))
                .retention(Duration.ofHours(24))
                .build();
        store = new DefaultMetricStore(config);
        engine = new QueryEngine(store);

        // Seed test data: counter that increments by 10 per second
        long baseTime = 1_000_000;
        for (int i = 0; i < 60; i++) {
            long ts = baseTime + i * 1000;
            store.write("http_requests_total", Map.of("method", "GET", "status", "200"), i * 10.0, ts);
            store.write("http_requests_total", Map.of("method", "GET", "status", "500"), i * 1.0, ts);
            store.write("http_requests_total", Map.of("method", "POST", "status", "200"), i * 5.0, ts);
        }
    }

    @Test
    void simpleVectorSelector() {
        QueryResult result = engine.instantQuery(
                "http_requests_total{method=\"GET\"}", 1_060_000);

        assertThat(result.getResultType()).isEqualTo(ResultType.VECTOR);
        assertThat(result.getSeries()).hasSize(2); // status=200 and status=500
    }

    @Test
    void rateFunction() {
        QueryResult result = engine.instantQuery(
                "rate(http_requests_total{method=\"GET\",status=\"200\"}[1m])", 1_060_000);

        assertThat(result.getSeries()).hasSize(1);
        double rate = result.getSeries().get(0).getSamples().get(0).getValue();
        assertThat(rate).isGreaterThan(0);
    }

    @Test
    void sumByAggregation() {
        QueryResult result = engine.instantQuery(
                "sum by (method)(http_requests_total)", 1_060_000);

        assertThat(result.getSeries()).hasSize(2); // GET and POST groups
    }

    @Test
    void rangeQuery() {
        QueryResult result = engine.rangeQuery(
                "http_requests_total{method=\"GET\",status=\"200\"}",
                1_000_000, 1_060_000, 15_000);

        assertThat(result.getResultType()).isEqualTo(ResultType.MATRIX);
        assertThat(result.getSeries()).isNotEmpty();
    }

    @Test
    void emptyResult() {
        QueryResult result = engine.instantQuery("nonexistent_metric", 1_060_000);
        assertThat(result.getSeries()).isEmpty();
    }
}
