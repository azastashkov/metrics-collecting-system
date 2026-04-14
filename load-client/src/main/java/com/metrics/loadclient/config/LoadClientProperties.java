package com.metrics.loadclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("loadclient")
public class LoadClientProperties {

    private int concurrency = 10;
    private int targetRps = 100;
    private MockServerConfig mockServer = new MockServerConfig();

    @Data
    public static class MockServerConfig {
        private int latencyMeanMs = 100;
        private int latencyStddevMs = 30;
        private double errorRate = 0.05;
    }
}
