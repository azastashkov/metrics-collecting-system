package com.metrics.tsdb.storage;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;
import java.time.Duration;

@Value
@Builder
public class MetricStoreConfig {
    @Builder.Default
    Path dataDirectory = Path.of("/data/tsdb");
    @Builder.Default
    Duration partitionDuration = Duration.ofHours(1);
    @Builder.Default
    Duration retention = Duration.ofHours(24);
}
