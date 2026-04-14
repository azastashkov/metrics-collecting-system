package com.metrics.tsdb.storage;

import com.metrics.tsdb.model.TimeSeries;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MetricStore {
    void write(String metricName, Map<String, String> labels, double value, long timestamp);

    List<TimeSeries> query(String metricName, Map<String, String> matchers, long startMs, long endMs);

    List<TimeSeries> queryWithOps(String metricName, List<SeriesIndex.LabelMatcher> matchers, long startMs, long endMs);

    Set<String> labelValues(String labelName);

    Set<String> metricNames();

    void start();

    void shutdown();
}
