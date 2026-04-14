package com.metrics.tsdb.parser.ast;

public sealed interface Expr permits
        VectorSelector, RangeVectorSelector, BinaryExpr,
        AggregationExpr, FunctionCallExpr {
}
