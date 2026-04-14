package com.metrics.loadclient.generator;

import com.metrics.loadclient.config.LoadClientProperties;
import com.metrics.loadclient.metrics.MetricsRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoadGenerator {

    private final LoadClientProperties properties;
    private final MetricsRegistry metricsRegistry;
    private final Environment environment;

    private ExecutorService virtualThreadExecutor;
    private ScheduledExecutorService scheduler;
    private HttpClient httpClient;

    @PostConstruct
    public void start() {
        String port = environment.getProperty("server.port", "8081");
        String baseUrl = "http://localhost:" + port + "/api/test";

        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        httpClient = HttpClient.newBuilder()
                .executor(virtualThreadExecutor)
                .build();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "load-scheduler");
            t.setDaemon(true);
            return t;
        });

        int targetRps = properties.getTargetRps();
        long periodMicros = 1_000_000L / targetRps;

        log.info("Starting load generation: targetRps={}, baseUrl={}", targetRps, baseUrl);

        scheduler.scheduleAtFixedRate(() -> {
            virtualThreadExecutor.submit(() -> sendRequest(baseUrl));
        }, 1_000_000, periodMicros, TimeUnit.MICROSECONDS);
    }

    private void sendRequest(String url) {
        long startNanos = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            double durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            metricsRegistry.recordRequest(response.statusCode(), durationSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            double durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            metricsRegistry.recordRequest(500, durationSeconds);
            log.debug("Request failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping load generation");
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (virtualThreadExecutor != null) {
            virtualThreadExecutor.close();
        }
    }
}
