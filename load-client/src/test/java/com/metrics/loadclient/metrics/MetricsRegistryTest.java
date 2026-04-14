package com.metrics.loadclient.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MetricsRegistryTest {

    private MetricsRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MetricsRegistry();
    }

    @Test
    void recordRequest_incrementsCountersFor200() {
        registry.recordRequest(200, 0.1);
        registry.recordRequest(200, 0.2);

        assertThat(registry.getRequestsTotal200()).isEqualTo(2);
        assertThat(registry.getRequestsTotal500()).isEqualTo(0);
        assertThat(registry.getErrorsTotal()).isEqualTo(0);
        assertThat(registry.getDurationCount()).isEqualTo(2);
        assertThat(registry.getDurationSum()).isCloseTo(0.3, within(0.001));
    }

    @Test
    void recordRequest_incrementsCountersFor500() {
        registry.recordRequest(500, 0.05);

        assertThat(registry.getRequestsTotal200()).isEqualTo(0);
        assertThat(registry.getRequestsTotal500()).isEqualTo(1);
        assertThat(registry.getErrorsTotal()).isEqualTo(1);
        assertThat(registry.getDurationCount()).isEqualTo(1);
        assertThat(registry.getDurationSum()).isCloseTo(0.05, within(0.001));
    }

    @Test
    void recordRequest_mixedStatusCodes() {
        registry.recordRequest(200, 0.1);
        registry.recordRequest(500, 0.2);
        registry.recordRequest(200, 0.3);
        registry.recordRequest(500, 0.4);

        assertThat(registry.getRequestsTotal200()).isEqualTo(2);
        assertThat(registry.getRequestsTotal500()).isEqualTo(2);
        assertThat(registry.getErrorsTotal()).isEqualTo(2);
        assertThat(registry.getDurationCount()).isEqualTo(4);
        assertThat(registry.getDurationSum()).isCloseTo(1.0, within(0.001));
    }

    @Test
    void getQuantile_returnsMedian() {
        for (int i = 1; i <= 100; i++) {
            registry.recordRequest(200, i * 0.01);
        }

        double median = registry.getQuantile(0.5);
        assertThat(median).isCloseTo(0.50, within(0.01));
    }

    @Test
    void getQuantile_returns95thPercentile() {
        for (int i = 1; i <= 100; i++) {
            registry.recordRequest(200, i * 0.01);
        }

        double p95 = registry.getQuantile(0.95);
        assertThat(p95).isCloseTo(0.95, within(0.01));
    }

    @Test
    void getQuantile_returns99thPercentile() {
        for (int i = 1; i <= 100; i++) {
            registry.recordRequest(200, i * 0.01);
        }

        double p99 = registry.getQuantile(0.99);
        assertThat(p99).isCloseTo(0.99, within(0.01));
    }

    @Test
    void getQuantile_emptyReturnsZero() {
        assertThat(registry.getQuantile(0.5)).isEqualTo(0.0);
    }

    @Test
    void getQuantile_singleObservation() {
        registry.recordRequest(200, 0.42);

        assertThat(registry.getQuantile(0.5)).isCloseTo(0.42, within(0.001));
        assertThat(registry.getQuantile(0.99)).isCloseTo(0.42, within(0.001));
    }

    @Test
    void inFlight_incrementAndDecrement() {
        assertThat(registry.getInFlight()).isEqualTo(0);

        registry.incrementInFlight();
        registry.incrementInFlight();
        assertThat(registry.getInFlight()).isEqualTo(2);

        registry.decrementInFlight();
        assertThat(registry.getInFlight()).isEqualTo(1);
    }

    @Test
    void trimRecentDurations_keepsMaxObservations() {
        for (int i = 0; i < 10_500; i++) {
            registry.recordRequest(200, i * 0.001);
        }

        registry.trimRecentDurations();

        // After trimming, quantile computation should still work
        // and use at most 10_000 observations
        double median = registry.getQuantile(0.5);
        assertThat(median).isGreaterThan(0.0);
    }
}
