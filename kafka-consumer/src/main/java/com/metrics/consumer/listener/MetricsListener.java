package com.metrics.consumer.listener;

import com.metrics.common.model.MetricSample;
import com.metrics.consumer.batch.BatchAccumulator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsListener {

    private final BatchAccumulator batchAccumulator;

    @KafkaListener(topics = "metrics-raw", groupId = "metrics-consumers")
    public void onMessage(List<MetricSample> samples) {
        log.debug("Received {} samples from Kafka", samples.size());
        for (MetricSample sample : samples) {
            batchAccumulator.add(sample);
        }
    }
}
