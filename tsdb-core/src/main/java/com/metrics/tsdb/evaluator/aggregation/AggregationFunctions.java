package com.metrics.tsdb.evaluator.aggregation;

import java.util.List;

public final class AggregationFunctions {

    private AggregationFunctions() {
    }

    public static double sum(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).sum();
    }

    public static double avg(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        return sum(values) / values.size();
    }

    public static double min(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
    }

    public static double max(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    public static double count(List<Double> values) {
        return values.size();
    }
}
