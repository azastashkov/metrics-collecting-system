package com.metrics.tsdb.server.config;

import com.metrics.tsdb.query.QueryEngine;
import com.metrics.tsdb.storage.DefaultMetricStore;
import com.metrics.tsdb.storage.MetricStore;
import com.metrics.tsdb.storage.MetricStoreConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class TsdbConfig {

    @Value("${tsdb.storage.data-dir:/data/tsdb}")
    private String dataDir;

    @Value("${tsdb.storage.partition-duration-ms:3600000}")
    private long partitionDurationMs;

    @Value("${tsdb.storage.retention-ms:86400000}")
    private long retentionMs;

    @Bean
    public MetricStoreConfig metricStoreConfig() {
        return MetricStoreConfig.builder()
                .dataDirectory(Path.of(dataDir))
                .partitionDuration(Duration.ofMillis(partitionDurationMs))
                .retention(Duration.ofMillis(retentionMs))
                .build();
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public MetricStore metricStore(MetricStoreConfig config) {
        return new DefaultMetricStore(config);
    }

    @Bean
    public QueryEngine queryEngine(MetricStore store) {
        return new QueryEngine(store);
    }
}
