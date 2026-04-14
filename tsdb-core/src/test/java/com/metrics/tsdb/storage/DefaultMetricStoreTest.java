package com.metrics.tsdb.storage;

import com.metrics.tsdb.model.TimeSeries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMetricStoreTest {

    private DefaultMetricStore store;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        MetricStoreConfig config = MetricStoreConfig.builder()
                .dataDirectory(tempDir)
                .partitionDuration(Duration.ofHours(1))
                .retention(Duration.ofHours(24))
                .build();
        store = new DefaultMetricStore(config);
    }

    @Test
    void writeThenQuery() {
        store.write("cpu_usage", Map.of("host", "server1"), 75.0, 1000);
        store.write("cpu_usage", Map.of("host", "server1"), 80.0, 2000);

        List<TimeSeries> result = store.query("cpu_usage", Map.of("host", "server1"), 0, 5000);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSamples()).hasSize(2);
    }

    @Test
    void multiSeriesQuery() {
        store.write("cpu_usage", Map.of("host", "server1"), 75.0, 1000);
        store.write("cpu_usage", Map.of("host", "server2"), 60.0, 1000);

        List<TimeSeries> result = store.query("cpu_usage", Map.of(), 0, 5000);

        assertThat(result).hasSize(2);
    }

    @Test
    void labelMatchingFilters() {
        store.write("http_requests", Map.of("method", "GET", "status", "200"), 100, 1000);
        store.write("http_requests", Map.of("method", "POST", "status", "200"), 50, 1000);

        List<TimeSeries> result = store.query("http_requests", Map.of("method", "GET"), 0, 5000);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKey().getLabels()).containsEntry("method", "GET");
    }

    @Test
    void emptyResult() {
        List<TimeSeries> result = store.query("nonexistent", Map.of(), 0, 5000);
        assertThat(result).isEmpty();
    }

    @Test
    void labelValues() {
        store.write("metric", Map.of("env", "prod"), 1, 1000);
        store.write("metric", Map.of("env", "staging"), 2, 1000);

        assertThat(store.labelValues("env")).containsExactlyInAnyOrder("prod", "staging");
    }

    @Test
    void metricNames() {
        store.write("metric_a", Map.of(), 1, 1000);
        store.write("metric_b", Map.of(), 2, 1000);

        assertThat(store.metricNames()).containsExactlyInAnyOrder("metric_a", "metric_b");
    }
}
