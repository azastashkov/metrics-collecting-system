package com.metrics.common.parser;

import com.metrics.common.model.MetricSample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusTextParserTest {

    @Test
    void parseMultiLineExposition() {
        String text = """
                # HELP http_requests_total Total HTTP requests
                # TYPE http_requests_total counter
                http_requests_total{method="GET",status="200"} 1234
                http_requests_total{method="GET",status="500"} 56
                """;

        List<MetricSample> samples = PrometheusTextParser.parse(text, 1000L);

        assertThat(samples).hasSize(2);
        assertThat(samples.get(0).getName()).isEqualTo("http_requests_total");
        assertThat(samples.get(0).getLabels()).containsEntry("method", "GET");
        assertThat(samples.get(0).getLabels()).containsEntry("status", "200");
        assertThat(samples.get(0).getValue()).isEqualTo(1234.0);
        assertThat(samples.get(0).getTimestamp()).isEqualTo(1000L);

        assertThat(samples.get(1).getLabels()).containsEntry("status", "500");
        assertThat(samples.get(1).getValue()).isEqualTo(56.0);
    }

    @Test
    void parseLineWithoutLabels() {
        String text = "process_cpu_seconds_total 42.5\n";

        List<MetricSample> samples = PrometheusTextParser.parse(text, 2000L);

        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).getName()).isEqualTo("process_cpu_seconds_total");
        assertThat(samples.get(0).getLabels()).isEmpty();
        assertThat(samples.get(0).getValue()).isEqualTo(42.5);
    }

    @Test
    void skipCommentLines() {
        String text = """
                # HELP my_metric A help text
                # TYPE my_metric gauge
                my_metric 10
                """;

        List<MetricSample> samples = PrometheusTextParser.parse(text, 3000L);
        assertThat(samples).hasSize(1);
    }

    @Test
    void handleEmptyInput() {
        assertThat(PrometheusTextParser.parse("", 1000L)).isEmpty();
        assertThat(PrometheusTextParser.parse(null, 1000L)).isEmpty();
        assertThat(PrometheusTextParser.parse("   ", 1000L)).isEmpty();
    }

    @Test
    void skipMalformedLines() {
        String text = """
                good_metric 42
                bad line without value
                another_good{label="val"} 99
                """;

        List<MetricSample> samples = PrometheusTextParser.parse(text, 1000L);
        assertThat(samples).hasSize(2);
    }

    @Test
    void parseFloatingPointValues() {
        String text = "metric_value{quantile=\"0.95\"} 0.00345\n";

        List<MetricSample> samples = PrometheusTextParser.parse(text, 1000L);

        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).getValue()).isEqualTo(0.00345);
        assertThat(samples.get(0).getLabels()).containsEntry("quantile", "0.95");
    }
}
