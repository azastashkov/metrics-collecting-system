package com.metrics.tsdb.parser.ast;

import lombok.Value;

import java.time.Duration;
import java.util.List;

@Value
public class RangeVectorSelector implements Expr {
    String metricName;
    List<LabelMatcher> matchers;
    Duration range;
}
