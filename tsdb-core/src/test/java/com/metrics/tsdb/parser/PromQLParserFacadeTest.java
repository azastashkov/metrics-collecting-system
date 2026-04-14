package com.metrics.tsdb.parser;

import com.metrics.tsdb.parser.ast.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromQLParserFacadeTest {

    private final PromQLParserFacade parser = new PromQLParserFacade();

    @Test
    void simpleVectorSelector() {
        Expr expr = parser.parse("http_requests_total");

        assertThat(expr).isInstanceOf(VectorSelector.class);
        VectorSelector vs = (VectorSelector) expr;
        assertThat(vs.getMetricName()).isEqualTo("http_requests_total");
        assertThat(vs.getMatchers()).isEmpty();
    }

    @Test
    void vectorSelectorWithLabels() {
        Expr expr = parser.parse("http_requests_total{method=\"GET\",status=\"200\"}");

        VectorSelector vs = (VectorSelector) expr;
        assertThat(vs.getMatchers()).hasSize(2);
        assertThat(vs.getMatchers().get(0).getLabelName()).isEqualTo("method");
        assertThat(vs.getMatchers().get(0).getType()).isEqualTo(MatchType.EQUAL);
        assertThat(vs.getMatchers().get(0).getValue()).isEqualTo("GET");
    }

    @Test
    void regexMatcher() {
        Expr expr = parser.parse("http_requests_total{method=~\"GET|POST\"}");

        VectorSelector vs = (VectorSelector) expr;
        assertThat(vs.getMatchers().get(0).getType()).isEqualTo(MatchType.REGEX_MATCH);
        assertThat(vs.getMatchers().get(0).getValue()).isEqualTo("GET|POST");
    }

    @Test
    void notEqualMatcher() {
        Expr expr = parser.parse("http_requests_total{status!=\"500\"}");

        VectorSelector vs = (VectorSelector) expr;
        assertThat(vs.getMatchers().get(0).getType()).isEqualTo(MatchType.NOT_EQUAL);
    }

    @Test
    void rangeVectorSelector() {
        Expr expr = parser.parse("http_requests_total[5m]");

        assertThat(expr).isInstanceOf(RangeVectorSelector.class);
        RangeVectorSelector rvs = (RangeVectorSelector) expr;
        assertThat(rvs.getMetricName()).isEqualTo("http_requests_total");
        assertThat(rvs.getRange()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void rateFunction() {
        Expr expr = parser.parse("rate(http_requests_total[5m])");

        assertThat(expr).isInstanceOf(FunctionCallExpr.class);
        FunctionCallExpr fc = (FunctionCallExpr) expr;
        assertThat(fc.getFunctionName()).isEqualTo("rate");
        assertThat(fc.getArgument()).isInstanceOf(RangeVectorSelector.class);
    }

    @Test
    void sumAggregationWithBy() {
        Expr expr = parser.parse("sum by (method)(rate(http_requests_total[5m]))");

        assertThat(expr).isInstanceOf(AggregationExpr.class);
        AggregationExpr ae = (AggregationExpr) expr;
        assertThat(ae.getOp()).isEqualTo(AggregateOp.SUM);
        assertThat(ae.getGrouping()).isEqualTo(GroupingType.BY);
        assertThat(ae.getGroupLabels()).containsExactly("method");
        assertThat(ae.getExpression()).isInstanceOf(FunctionCallExpr.class);
    }

    @Test
    void aggregationWithTrailingGrouping() {
        Expr expr = parser.parse("sum(rate(http_requests_total[5m])) by (method)");

        AggregationExpr ae = (AggregationExpr) expr;
        assertThat(ae.getGrouping()).isEqualTo(GroupingType.BY);
        assertThat(ae.getGroupLabels()).containsExactly("method");
    }

    @Test
    void binaryExpression() {
        Expr expr = parser.parse("metric_a + metric_b");

        assertThat(expr).isInstanceOf(BinaryExpr.class);
        BinaryExpr be = (BinaryExpr) expr;
        assertThat(be.getOp()).isEqualTo(BinaryOp.ADD);
    }

    @Test
    void binaryExpressionWithParens() {
        Expr expr = parser.parse("(metric_a + metric_b) * metric_c");

        assertThat(expr).isInstanceOf(BinaryExpr.class);
        BinaryExpr be = (BinaryExpr) expr;
        assertThat(be.getOp()).isEqualTo(BinaryOp.MUL);
        assertThat(be.getLeft()).isInstanceOf(BinaryExpr.class);
    }

    @Test
    void syntaxErrorThrowsParseException() {
        assertThatThrownBy(() -> parser.parse("{invalid"))
                .isInstanceOf(ParseException.class);
    }

    @Test
    void irateFunction() {
        Expr expr = parser.parse("irate(http_requests_total[1m])");

        FunctionCallExpr fc = (FunctionCallExpr) expr;
        assertThat(fc.getFunctionName()).isEqualTo("irate");
    }

    @Test
    void avgAggregation() {
        Expr expr = parser.parse("avg(cpu_usage)");

        AggregationExpr ae = (AggregationExpr) expr;
        assertThat(ae.getOp()).isEqualTo(AggregateOp.AVG);
        assertThat(ae.getGrouping()).isNull();
    }
}
