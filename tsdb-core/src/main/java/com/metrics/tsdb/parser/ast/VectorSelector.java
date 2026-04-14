package com.metrics.tsdb.parser.ast;

import lombok.Value;

import java.util.List;

@Value
public class VectorSelector implements Expr {
    String metricName;
    List<LabelMatcher> matchers;
}
