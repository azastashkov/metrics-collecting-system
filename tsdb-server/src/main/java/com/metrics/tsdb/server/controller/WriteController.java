package com.metrics.tsdb.server.controller;

import com.metrics.common.model.MetricSample;
import com.metrics.tsdb.storage.MetricStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class WriteController {

    private final MetricStore metricStore;

    public WriteController(MetricStore metricStore) {
        this.metricStore = metricStore;
    }

    @PostMapping("/api/v1/write")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void write(@RequestBody List<MetricSample> samples) {
        for (MetricSample sample : samples) {
            metricStore.write(sample.getName(), sample.getLabels(), sample.getValue(), sample.getTimestamp());
        }
    }
}
