package com.metrics.consumer.client;

import com.metrics.common.model.MetricSample;
import com.metrics.consumer.config.ConsumerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class TsdbWriteClient {

    private final WebClient webClient;
    private final ConsumerProperties properties;

    public TsdbWriteClient(ConsumerProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.create();
    }

    public void write(List<MetricSample> batch) {
        webClient.post()
                .uri(properties.getWriteUrl())
                .bodyValue(batch)
                .retrieve()
                .toBodilessEntity()
                .retryWhen(Retry.backoff(properties.getMaxRetryAttempts(), Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30)))
                .doOnSuccess(response -> log.debug("Successfully wrote batch of {} samples", batch.size()))
                .doOnError(e -> log.error("Failed to write batch of {} samples after retries: {}",
                        batch.size(), e.getMessage()))
                .onErrorComplete()
                .block();
    }
}
