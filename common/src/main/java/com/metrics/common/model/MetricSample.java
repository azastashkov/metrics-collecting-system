package com.metrics.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricSample {
    private String name;
    private Map<String, String> labels;
    private double value;
    private long timestamp;
}
