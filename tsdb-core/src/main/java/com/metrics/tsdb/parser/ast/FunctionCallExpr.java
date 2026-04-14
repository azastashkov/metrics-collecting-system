package com.metrics.tsdb.parser.ast;

import lombok.Value;

@Value
public class FunctionCallExpr implements Expr {
    String functionName;
    Expr argument;
}
