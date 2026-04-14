package com.metrics.tsdb.storage;

import com.metrics.tsdb.model.Sample;
import com.metrics.tsdb.storage.partition.SeriesData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeriesDataTest {

    @Test
    void appendAndRange() {
        SeriesData data = new SeriesData();
        data.append(1000, 1.0);
        data.append(2000, 2.0);
        data.append(3000, 3.0);

        List<Sample> samples = data.range(1000, 3000);
        assertThat(samples).hasSize(3);
        assertThat(samples.get(0).getValue()).isEqualTo(1.0);
        assertThat(samples.get(2).getValue()).isEqualTo(3.0);
    }

    @Test
    void rangeFiltersCorrectly() {
        SeriesData data = new SeriesData();
        for (int i = 0; i < 10; i++) {
            data.append(i * 1000, i);
        }

        List<Sample> samples = data.range(3000, 6000);
        assertThat(samples).hasSize(4);
        assertThat(samples.get(0).getTimestamp()).isEqualTo(3000);
        assertThat(samples.get(3).getTimestamp()).isEqualTo(6000);
    }

    @Test
    void emptyRange() {
        SeriesData data = new SeriesData();
        assertThat(data.range(0, 1000)).isEmpty();
    }

    @Test
    void dropsOutOfOrderSamples() {
        SeriesData data = new SeriesData();
        data.append(2000, 2.0);
        data.append(1000, 1.0); // should be dropped
        data.append(3000, 3.0);

        assertThat(data.size()).isEqualTo(2);
        List<Sample> samples = data.range(0, 5000);
        assertThat(samples).hasSize(2);
        assertThat(samples.get(0).getTimestamp()).isEqualTo(2000);
    }

    @Test
    void growsBeyondInitialCapacity() {
        SeriesData data = new SeriesData();
        for (int i = 0; i < 500; i++) {
            data.append(i * 1000, i);
        }
        assertThat(data.size()).isEqualTo(500);
        assertThat(data.range(0, 499_000)).hasSize(500);
    }
}
