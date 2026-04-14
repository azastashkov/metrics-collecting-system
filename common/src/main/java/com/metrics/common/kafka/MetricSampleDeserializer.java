package com.metrics.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metrics.common.model.MetricSample;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;

public class MetricSampleDeserializer implements Deserializer<MetricSample> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public MetricSample deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.readValue(data, MetricSample.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize MetricSample", e);
        }
    }
}
