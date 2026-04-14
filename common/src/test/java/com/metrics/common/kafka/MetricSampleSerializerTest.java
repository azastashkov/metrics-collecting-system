package com.metrics.common.kafka;

import com.metrics.common.model.MetricSample;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetricSampleSerializerTest {

    private final MetricSampleSerializer serializer = new MetricSampleSerializer();
    private final MetricSampleDeserializer deserializer = new MetricSampleDeserializer();

    @Test
    void roundTrip() {
        MetricSample original = MetricSample.builder()
                .name("http_requests_total")
                .labels(Map.of("method", "GET", "status", "200"))
                .value(1234.0)
                .timestamp(1000L)
                .build();

        byte[] bytes = serializer.serialize("test-topic", original);
        MetricSample deserialized = deserializer.deserialize("test-topic", bytes);

        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void handleNull() {
        assertThat(serializer.serialize("topic", null)).isNull();
        assertThat(deserializer.deserialize("topic", null)).isNull();
    }
}
