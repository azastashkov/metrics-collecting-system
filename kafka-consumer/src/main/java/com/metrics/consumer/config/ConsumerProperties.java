package com.metrics.consumer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties("consumer.tsdb")
public class ConsumerProperties {

    private String writeUrl;
    private int batchSize = 500;
    private long flushIntervalMs = 5000;
    private int maxRetryAttempts = 5;
}
