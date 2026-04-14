package com.metrics.tsdb.parser.ast;

import lombok.Value;

import java.util.List;

@Value
public class AggregationExpr implements Expr {
    AggregateOp op;
    Expr expression;
    GroupingType grouping;   // may be null
    List<String> groupLabels; // may be empty
}
