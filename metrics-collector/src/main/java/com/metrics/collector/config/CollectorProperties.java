package com.metrics.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties("collector")
public class CollectorProperties {

    private List<ScrapeTarget> targets;

    @Data
    public static class ScrapeTarget {
        private String url;
        private long intervalMs = 15000;
    }
}
