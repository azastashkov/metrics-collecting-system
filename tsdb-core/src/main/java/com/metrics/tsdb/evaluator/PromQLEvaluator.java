package com.metrics.tsdb.evaluator;

import com.metrics.tsdb.evaluator.aggregation.AggregationFunctions;
import com.metrics.tsdb.evaluator.function.RateFunctions;
import com.metrics.tsdb.model.Sample;
import com.metrics.tsdb.model.SeriesKey;
import com.metrics.tsdb.model.TimeSeries;
import com.metrics.tsdb.parser.ast.AggregationExpr;
import com.metrics.tsdb.parser.ast.BinaryExpr;
import com.metrics.tsdb.parser.ast.Expr;
import com.metrics.tsdb.parser.ast.FunctionCallExpr;
import com.metrics.tsdb.parser.ast.GroupingType;
import com.metrics.tsdb.parser.ast.LabelMatcher;
import com.metrics.tsdb.parser.ast.RangeVectorSelector;
import com.metrics.tsdb.parser.ast.VectorSelector;
import com.metrics.tsdb.query.QueryResult;
import com.metrics.tsdb.query.ResultType;
import com.metrics.tsdb.storage.MetricStore;
import com.metrics.tsdb.storage.SeriesIndex;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PromQLEvaluator {

    private static final long DEFAULT_LOOKBACK_MS = 300_000; // 5 minutes

    private final MetricStore store;

    public PromQLEvaluator(MetricStore store) {
        this.store = store;
    }

    public QueryResult evaluate(Expr expr, long evalTimestamp) {
        return switch (expr) {
            case VectorSelector vs -> evalVectorSelector(vs, evalTimestamp);
            case RangeVectorSelector rvs -> evalRangeVectorSelector(rvs, evalTimestamp);
            case FunctionCallExpr fc -> evalFunctionCall(fc, evalTimestamp);
            case AggregationExpr ae -> evalAggregation(ae, evalTimestamp);
            case BinaryExpr be -> evalBinaryExpr(be, evalTimestamp);
        };
    }

    public QueryResult evaluateRange(Expr expr, long startMs, long endMs, long stepMs) {
        Map<SeriesKey, List<Sample>> seriesMap = new LinkedHashMap<>();

        for (long ts = startMs; ts <= endMs; ts += stepMs) {
            QueryResult stepResult = evaluate(expr, ts);
            for (TimeSeries series : stepResult.getSeries()) {
                seriesMap.computeIfAbsent(series.getKey(), k -> new ArrayList<>())
                        .addAll(series.getSamples());
            }
        }

        List<TimeSeries> resultSeries = seriesMap.entrySet().stream()
                .map(e -> TimeSeries.builder().key(e.getKey()).samples(e.getValue()).build())
                .toList();

        return QueryResult.builder()
                .resultType(ResultType.MATRIX)
                .series(resultSeries)
                .build();
    }

    private QueryResult evalVectorSelector(VectorSelector vs, long evalTimestamp) {
        long startMs = evalTimestamp - DEFAULT_LOOKBACK_MS;
        List<SeriesIndex.LabelMatcher> matchers = convertMatchers(vs.getMatchers());
        List<TimeSeries> series = store.queryWithOps(vs.getMetricName(), matchers, startMs, evalTimestamp);

        // For instant vector: keep only the last sample per series
        List<TimeSeries> instantSeries = series.stream()
                .map(ts -> {
                    if (ts.getSamples().isEmpty()) return ts;
                    Sample lastSample = ts.getSamples().get(ts.getSamples().size() - 1);
                    return TimeSeries.builder()
                            .key(ts.getKey())
                            .samples(List.of(lastSample))
                            .build();
                })
                .filter(ts -> !ts.getSamples().isEmpty())
                .toList();

        return QueryResult.builder()
                .resultType(ResultType.VECTOR)
                .series(instantSeries)
                .build();
    }

    private QueryResult evalRangeVectorSelector(RangeVectorSelector rvs, long evalTimestamp) {
        long rangeMs = rvs.getRange().toMillis();
        long startMs = evalTimestamp - rangeMs;
        List<SeriesIndex.LabelMatcher> matchers = convertMatchers(rvs.getMatchers());
        List<TimeSeries> series = store.queryWithOps(rvs.getMetricName(), matchers, startMs, evalTimestamp);

        return QueryResult.builder()
                .resultType(ResultType.MATRIX)
                .series(series)
                .build();
    }

    private QueryResult evalFunctionCall(FunctionCallExpr fc, long evalTimestamp) {
        QueryResult argResult = evaluate(fc.getArgument(), evalTimestamp);

        List<TimeSeries> resultSeries = new ArrayList<>();
        for (TimeSeries ts : argResult.getSeries()) {
            double value = switch (fc.getFunctionName()) {
                case "rate" -> {
                    Duration range = extractRange(fc.getArgument());
                    yield RateFunctions.rate(ts.getSamples(), range);
                }
                case "irate" -> RateFunctions.irate(ts.getSamples());
                case "increase" -> RateFunctions.increase(ts.getSamples());
                default -> throw new IllegalArgumentException("Unknown function: " + fc.getFunctionName());
            };

            resultSeries.add(TimeSeries.builder()
                    .key(ts.getKey())
                    .samples(List.of(new Sample(evalTimestamp, value)))
                    .build());
        }

        return QueryResult.builder()
                .resultType(ResultType.VECTOR)
                .series(resultSeries)
                .build();
    }

    private QueryResult evalAggregation(AggregationExpr ae, long evalTimestamp) {
        QueryResult innerResult = evaluate(ae.getExpression(), evalTimestamp);

        // Group series by the grouping labels
        Map<Map<String, String>, List<TimeSeries>> groups = new LinkedHashMap<>();

        for (TimeSeries ts : innerResult.getSeries()) {
            Map<String, String> groupKey = computeGroupKey(ts.getKey(), ae.getGrouping(), ae.getGroupLabels());
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(ts);
        }

        List<TimeSeries> resultSeries = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            List<Double> values = entry.getValue().stream()
                    .flatMap(ts -> ts.getSamples().stream())
                    .map(Sample::getValue)
                    .toList();

            double aggregatedValue = switch (ae.getOp()) {
                case SUM -> AggregationFunctions.sum(values);
                case AVG -> AggregationFunctions.avg(values);
                case MIN -> AggregationFunctions.min(values);
                case MAX -> AggregationFunctions.max(values);
                case COUNT -> AggregationFunctions.count(values);
            };

            SeriesKey groupSeriesKey = new SeriesKey(
                    entry.getValue().get(0).getKey().getMetricName(),
                    entry.getKey());

            resultSeries.add(TimeSeries.builder()
                    .key(groupSeriesKey)
                    .samples(List.of(new Sample(evalTimestamp, aggregatedValue)))
                    .build());
        }

        return QueryResult.builder()
                .resultType(ResultType.VECTOR)
                .series(resultSeries)
                .build();
    }

    private QueryResult evalBinaryExpr(BinaryExpr be, long evalTimestamp) {
        QueryResult leftResult = evaluate(be.getLeft(), evalTimestamp);
        QueryResult rightResult = evaluate(be.getRight(), evalTimestamp);

        // Match series by label set
        Map<Map<String, String>, TimeSeries> rightMap = new HashMap<>();
        for (TimeSeries ts : rightResult.getSeries()) {
            rightMap.put(new TreeMap<>(ts.getKey().getLabels()), ts);
        }

        List<TimeSeries> resultSeries = new ArrayList<>();
        for (TimeSeries leftTs : leftResult.getSeries()) {
            Map<String, String> labels = new TreeMap<>(leftTs.getKey().getLabels());
            TimeSeries rightTs = rightMap.get(labels);

            if (rightTs == null) continue;

            double leftVal = leftTs.getSamples().isEmpty() ? 0 : leftTs.getSamples().get(0).getValue();
            double rightVal = rightTs.getSamples().isEmpty() ? 0 : rightTs.getSamples().get(0).getValue();

            double result = switch (be.getOp()) {
                case ADD -> leftVal + rightVal;
                case SUB -> leftVal - rightVal;
                case MUL -> leftVal * rightVal;
                case DIV -> rightVal == 0 ? Double.POSITIVE_INFINITY : leftVal / rightVal;
            };

            resultSeries.add(TimeSeries.builder()
                    .key(leftTs.getKey())
                    .samples(List.of(new Sample(evalTimestamp, result)))
                    .build());
        }

        return QueryResult.builder()
                .resultType(ResultType.VECTOR)
                .series(resultSeries)
                .build();
    }

    private Map<String, String> computeGroupKey(SeriesKey key, GroupingType grouping, List<String> groupLabels) {
        if (grouping == null || groupLabels.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new TreeMap<>();
        if (grouping == GroupingType.BY) {
            for (String label : groupLabels) {
                String val = key.getLabels().get(label);
                if (val != null) {
                    result.put(label, val);
                }
            }
        } else { // WITHOUT
            result.putAll(key.getLabels());
            for (String label : groupLabels) {
                result.remove(label);
            }
        }
        return result;
    }

    private Duration extractRange(Expr expr) {
        if (expr instanceof RangeVectorSelector rvs) {
            return rvs.getRange();
        }
        return Duration.ofMinutes(1); // default fallback
    }

    private List<SeriesIndex.LabelMatcher> convertMatchers(List<LabelMatcher> astMatchers) {
        return astMatchers.stream()
                .map(m -> new SeriesIndex.LabelMatcher(
                        m.getLabelName(),
                        switch (m.getType()) {
                            case EQUAL -> SeriesIndex.LabelMatcher.MatchType.EQUAL;
                            case NOT_EQUAL -> SeriesIndex.LabelMatcher.MatchType.NOT_EQUAL;
                            case REGEX_MATCH -> SeriesIndex.LabelMatcher.MatchType.REGEX_MATCH;
                            case REGEX_NOT_MATCH -> SeriesIndex.LabelMatcher.MatchType.REGEX_NOT_MATCH;
                        },
                        m.getValue()))
                .toList();
    }
}
