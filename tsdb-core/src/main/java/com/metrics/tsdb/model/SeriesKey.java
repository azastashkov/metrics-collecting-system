package com.metrics.tsdb.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@Getter
@EqualsAndHashCode
public class SeriesKey {
    private final String metricName;
    private final SortedMap<String, String> labels;

    public SeriesKey(String metricName, Map<String, String> labels) {
        this.metricName = metricName;
        this.labels = Collections.unmodifiableSortedMap(new TreeMap<>(labels));
    }
}
