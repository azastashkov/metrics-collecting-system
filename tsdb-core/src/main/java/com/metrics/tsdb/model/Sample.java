package com.metrics.tsdb.model;

import lombok.Value;

@Value
public class Sample {
    long timestamp;
    double value;
}
