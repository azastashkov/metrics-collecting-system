package com.metrics.tsdb.query;

import com.metrics.tsdb.evaluator.PromQLEvaluator;
import com.metrics.tsdb.parser.PromQLParserFacade;
import com.metrics.tsdb.parser.ast.Expr;
import com.metrics.tsdb.storage.MetricStore;

public class QueryEngine {

    private final PromQLParserFacade parser;
    private final PromQLEvaluator evaluator;

    public QueryEngine(MetricStore store) {
        this.parser = new PromQLParserFacade();
        this.evaluator = new PromQLEvaluator(store);
    }

    public QueryResult instantQuery(String promql, long evalTimestamp) {
        Expr expr = parser.parse(promql);
        return evaluator.evaluate(expr, evalTimestamp);
    }

    public QueryResult rangeQuery(String promql, long startMs, long endMs, long stepMs) {
        Expr expr = parser.parse(promql);
        return evaluator.evaluateRange(expr, startMs, endMs, stepMs);
    }
}
