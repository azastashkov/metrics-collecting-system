package com.metrics.tsdb.server.controller;

import com.metrics.tsdb.model.Sample;
import com.metrics.tsdb.model.TimeSeries;
import com.metrics.tsdb.query.QueryEngine;
import com.metrics.tsdb.query.QueryResult;
import com.metrics.tsdb.query.ResultType;
import com.metrics.tsdb.storage.MetricStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class QueryController {

    private static final Pattern STEP_PATTERN = Pattern.compile("^(\\d+)([smhd])$");

    private final QueryEngine queryEngine;
    private final MetricStore metricStore;

    public QueryController(QueryEngine queryEngine, MetricStore metricStore) {
        this.queryEngine = queryEngine;
        this.metricStore = metricStore;
    }

    @GetMapping("/api/v1/query")
    public Map<String, Object> instantQuery(
            @RequestParam("query") String query,
            @RequestParam(value = "time", required = false) Double time) {

        long evalTimestamp = (time != null)
                ? (long) (time * 1000)
                : System.currentTimeMillis();

        QueryResult result = queryEngine.instantQuery(query, evalTimestamp);
        return buildResponse(result);
    }

    @GetMapping("/api/v1/query_range")
    public Map<String, Object> rangeQuery(
            @RequestParam("query") String query,
            @RequestParam("start") Double start,
            @RequestParam("end") Double end,
            @RequestParam("step") String step) {

        long startMs = (long) (start * 1000);
        long endMs = (long) (end * 1000);
        long stepMs = parseStep(step);

        QueryResult result = queryEngine.rangeQuery(query, startMs, endMs, stepMs);
        return buildResponse(result);
    }

    @GetMapping("/api/v1/label/{name}/values")
    public Map<String, Object> labelValues(@PathVariable("name") String name) {
        Set<String> values = metricStore.labelValues(name);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", values);
        return response;
    }

    @GetMapping("/api/v1/series")
    public Map<String, Object> series(@RequestParam("match[]") String match) {
        long endMs = System.currentTimeMillis();
        long startMs = endMs - 300_000;

        QueryResult result = queryEngine.instantQuery(match, endMs);

        List<Map<String, String>> seriesList = new ArrayList<>();
        if (result.getSeries() != null) {
            for (TimeSeries ts : result.getSeries()) {
                Map<String, String> metricMap = new LinkedHashMap<>();
                metricMap.put("__name__", ts.getKey().getMetricName());
                metricMap.putAll(ts.getKey().getLabels());
                seriesList.add(metricMap);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", seriesList);
        return response;
    }

    private Map<String, Object> buildResponse(QueryResult result) {
        String resultType = result.getResultType() == ResultType.VECTOR ? "vector" : "matrix";
        List<Map<String, Object>> resultList = new ArrayList<>();

        if (result.getSeries() != null) {
            for (TimeSeries ts : result.getSeries()) {
                Map<String, Object> element = new LinkedHashMap<>();

                Map<String, String> metricMap = new LinkedHashMap<>();
                metricMap.put("__name__", ts.getKey().getMetricName());
                metricMap.putAll(ts.getKey().getLabels());
                element.put("metric", metricMap);

                if (result.getResultType() == ResultType.VECTOR) {
                    if (ts.getSamples() != null && !ts.getSamples().isEmpty()) {
                        Sample sample = ts.getSamples().get(ts.getSamples().size() - 1);
                        List<Object> value = List.of(
                                sample.getTimestamp() / 1000.0,
                                String.valueOf(sample.getValue()));
                        element.put("value", value);
                    } else {
                        element.put("value", List.of(0, "0"));
                    }
                } else {
                    List<List<Object>> values = new ArrayList<>();
                    if (ts.getSamples() != null) {
                        for (Sample sample : ts.getSamples()) {
                            values.add(List.of(
                                    sample.getTimestamp() / 1000.0,
                                    String.valueOf(sample.getValue())));
                        }
                    }
                    element.put("values", values);
                }

                resultList.add(element);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resultType", resultType);
        data.put("result", resultList);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", data);
        return response;
    }

    private long parseStep(String step) {
        Matcher matcher = STEP_PATTERN.matcher(step);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid step format: " + step);
        }
        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);
        return switch (unit) {
            case "s" -> amount * 1000;
            case "m" -> amount * 60 * 1000;
            case "h" -> amount * 3600 * 1000;
            case "d" -> amount * 86400 * 1000;
            default -> throw new IllegalArgumentException("Unknown step unit: " + unit);
        };
    }
}
