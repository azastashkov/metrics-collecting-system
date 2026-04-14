package com.metrics.tsdb.parser.ast;

import lombok.Value;

@Value
public class BinaryExpr implements Expr {
    Expr left;
    BinaryOp op;
    Expr right;
}
