package com.metrics.collector.scrape;

import com.metrics.collector.config.CollectorProperties;
import com.metrics.common.constants.MetricsConstants;
import com.metrics.common.model.MetricSample;
import com.metrics.common.parser.PrometheusTextParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapeService {

    private final KafkaTemplate<String, MetricSample> kafkaTemplate;
    private final HttpClient httpClient;

    public void scrape(CollectorProperties.ScrapeTarget target) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target.getUrl()))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Scrape of {} returned status {}", target.getUrl(), response.statusCode());
                return;
            }

            long scrapeTimestamp = System.currentTimeMillis();
            List<MetricSample> samples = PrometheusTextParser.parse(response.body(), scrapeTimestamp);

            log.debug("Scraped {} samples from {}", samples.size(), target.getUrl());

            for (MetricSample sample : samples) {
                kafkaTemplate.send(MetricsConstants.TOPIC_METRICS_RAW, sample.getName(), sample);
            }
        } catch (Exception e) {
            log.error("Error scraping {}: {}", target.getUrl(), e.getMessage(), e);
        }
    }
}
