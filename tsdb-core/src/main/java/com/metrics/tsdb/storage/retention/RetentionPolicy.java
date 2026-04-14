package com.metrics.tsdb.storage.retention;

import lombok.Value;

import java.time.Duration;

@Value
public class RetentionPolicy {
    Duration retention;
}
