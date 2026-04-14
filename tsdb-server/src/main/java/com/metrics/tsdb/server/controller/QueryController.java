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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
public class QueryController {

    private static final Pattern STEP_PATTERN = Pattern.compile("^(\\d+\\.?\\d*)([smhd])?$");

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
            @RequestParam("start") String start,
            @RequestParam("end") String end,
            @RequestParam("step") String step) {

        long startMs = (long) (Double.parseDouble(start) * 1000);
        long endMs = (long) (Double.parseDouble(end) * 1000);
        long stepMs = parseStep(step);

        // Cap maximum steps to prevent OOM on wide queries
        long maxSteps = 11000;
        if ((endMs - startMs) / stepMs > maxSteps) {
            stepMs = (endMs - startMs) / maxSteps;
        }

        QueryResult result = queryEngine.rangeQuery(query, startMs, endMs, stepMs);
        return buildResponse(result);
    }

    @GetMapping("/api/v1/label/{name}/values")
    public Map<String, Object> labelValues(@PathVariable("name") String name) {
        Set<String> values = metricStore.labelValues(name);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", List.copyOf(values));
        return response;
    }

    @GetMapping("/api/v1/labels")
    public Map<String, Object> labels() {
        Set<String> allLabels = metricStore.metricNames().stream()
                .map(n -> "__name__")
                .collect(Collectors.toSet());
        // Collect all label keys from all series
        for (var key : metricStore.labelValues("__all_keys__")) {
            allLabels.add(key);
        }
        // Fallback: get known label names by querying all series
        allLabels.addAll(getAllLabelNames());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", List.copyOf(allLabels));
        return response;
    }

    @GetMapping("/api/v1/status/buildinfo")
    public Map<String, Object> buildInfo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", "1.0.0");
        data.put("revision", "custom-tsdb");
        data.put("branch", "main");
        data.put("goVersion", "java21");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", data);
        return response;
    }

    @GetMapping("/api/v1/series")
    public Map<String, Object> series(
            @RequestParam(value = "match[]", required = false) String match,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end) {

        String query = (match != null && !match.isBlank()) ? match : ".*";
        long endMs = System.currentTimeMillis();

        QueryResult result;
        try {
            result = queryEngine.instantQuery(query, endMs);
        } catch (Exception e) {
            // If the match expression is not parseable, return empty
            result = QueryResult.builder()
                    .resultType(ResultType.VECTOR)
                    .series(List.of())
                    .build();
        }

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

    private Set<String> getAllLabelNames() {
        Set<String> labelNames = new java.util.HashSet<>();
        for (String metricName : metricStore.metricNames()) {
            try {
                QueryResult result = queryEngine.instantQuery(metricName, System.currentTimeMillis());
                for (TimeSeries ts : result.getSeries()) {
                    labelNames.addAll(ts.getKey().getLabels().keySet());
                }
            } catch (Exception ignored) {
            }
        }
        return labelNames;
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
        double amount = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null) {
            // Bare number = seconds
            return (long) (amount * 1000);
        }
        return switch (unit) {
            case "s" -> (long) (amount * 1000);
            case "m" -> (long) (amount * 60 * 1000);
            case "h" -> (long) (amount * 3600 * 1000);
            case "d" -> (long) (amount * 86400 * 1000);
            default -> throw new IllegalArgumentException("Unknown step unit: " + unit);
        };
    }
}
