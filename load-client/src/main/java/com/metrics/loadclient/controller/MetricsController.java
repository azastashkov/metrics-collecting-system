package com.metrics.loadclient.controller;

import com.metrics.loadclient.metrics.MetricsRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsRegistry metricsRegistry;

    @GetMapping(value = "/metrics", produces = "text/plain")
    public String metrics() {
        StringBuilder sb = new StringBuilder();

        sb.append("# HELP http_requests_total Total HTTP requests\n");
        sb.append("# TYPE http_requests_total counter\n");
        sb.append("http_requests_total{method=\"GET\",status=\"200\"} ")
                .append(metricsRegistry.getRequestsTotal200()).append('\n');
        sb.append("http_requests_total{method=\"GET\",status=\"500\"} ")
                .append(metricsRegistry.getRequestsTotal500()).append('\n');

        sb.append("# HELP http_request_duration_seconds HTTP request duration\n");
        sb.append("# TYPE http_request_duration_seconds summary\n");
        sb.append("http_request_duration_seconds{method=\"GET\",quantile=\"0.5\"} ")
                .append(String.format("%.6f", metricsRegistry.getQuantile(0.5))).append('\n');
        sb.append("http_request_duration_seconds{method=\"GET\",quantile=\"0.95\"} ")
                .append(String.format("%.6f", metricsRegistry.getQuantile(0.95))).append('\n');
        sb.append("http_request_duration_seconds{method=\"GET\",quantile=\"0.99\"} ")
                .append(String.format("%.6f", metricsRegistry.getQuantile(0.99))).append('\n');
        sb.append("http_request_duration_seconds_sum{method=\"GET\"} ")
                .append(String.format("%.1f", metricsRegistry.getDurationSum())).append('\n');
        sb.append("http_request_duration_seconds_count{method=\"GET\"} ")
                .append(metricsRegistry.getDurationCount()).append('\n');

        sb.append("# HELP http_request_errors_total Total HTTP errors\n");
        sb.append("# TYPE http_request_errors_total counter\n");
        sb.append("http_request_errors_total{method=\"GET\"} ")
                .append(metricsRegistry.getErrorsTotal()).append('\n');

        sb.append("# HELP http_requests_in_flight Current in-flight requests\n");
        sb.append("# TYPE http_requests_in_flight gauge\n");
        sb.append("http_requests_in_flight ")
                .append(metricsRegistry.getInFlight()).append('\n');

        return sb.toString();
    }
}
