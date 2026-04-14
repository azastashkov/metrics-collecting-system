package com.metrics.loadclient.controller;

import com.metrics.loadclient.config.LoadClientProperties;
import com.metrics.loadclient.metrics.MetricsRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
@RequiredArgsConstructor
public class MockServerController {

    private final MetricsRegistry metricsRegistry;
    private final LoadClientProperties properties;
    private final Random random = new Random();

    @GetMapping("/api/test")
    public ResponseEntity<String> test() {
        metricsRegistry.incrementInFlight();
        long startNanos = System.nanoTime();
        try {
            LoadClientProperties.MockServerConfig config = properties.getMockServer();
            long latencyMs = (long) (config.getLatencyMeanMs()
                    + random.nextGaussian() * config.getLatencyStddevMs());
            latencyMs = Math.max(0, latencyMs);
            Thread.sleep(latencyMs);

            boolean isError = random.nextDouble() < config.getErrorRate();
            int statusCode = isError ? 500 : 200;
            double durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            metricsRegistry.recordRequest(statusCode, durationSeconds);

            if (isError) {
                return ResponseEntity.internalServerError().body("Internal Server Error");
            }
            return ResponseEntity.ok("OK");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            double durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            metricsRegistry.recordRequest(500, durationSeconds);
            return ResponseEntity.internalServerError().body("Interrupted");
        } finally {
            metricsRegistry.decrementInFlight();
        }
    }
}
