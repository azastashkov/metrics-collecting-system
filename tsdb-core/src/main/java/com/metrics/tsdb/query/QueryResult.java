package com.metrics.tsdb.query;

import com.metrics.tsdb.model.TimeSeries;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class QueryResult {
    ResultType resultType;
    List<TimeSeries> series;
}
