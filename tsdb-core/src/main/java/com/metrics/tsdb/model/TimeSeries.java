package com.metrics.tsdb.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class TimeSeries {
    SeriesKey key;
    List<Sample> samples;
}
