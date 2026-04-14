package com.metrics.tsdb.storage.partition;

import com.metrics.tsdb.model.Sample;
import com.metrics.tsdb.model.SeriesId;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TimePartition {

    private final long startTimeMs;
    private final long endTimeMs;
    private final ConcurrentHashMap<SeriesId, SeriesData> seriesDataMap = new ConcurrentHashMap<>();

    public TimePartition(long startTimeMs, long durationMs) {
        this.startTimeMs = startTimeMs;
        this.endTimeMs = startTimeMs + durationMs;
    }

    public void write(SeriesId id, long timestamp, double value) {
        SeriesData data = seriesDataMap.computeIfAbsent(id, k -> new SeriesData());
        data.append(timestamp, value);
    }

    public List<Sample> read(SeriesId id, long startMs, long endMs) {
        SeriesData data = seriesDataMap.get(id);
        if (data == null) {
            return List.of();
        }
        return data.range(startMs, endMs);
    }

    public boolean contains(long timestamp) {
        return timestamp >= startTimeMs && timestamp < endTimeMs;
    }

    public boolean isComplete(long currentTimeMs) {
        return currentTimeMs >= endTimeMs;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public long getEndTimeMs() {
        return endTimeMs;
    }

    public Set<SeriesId> getSeriesIds() {
        return seriesDataMap.keySet();
    }

    public SeriesData getSeriesData(SeriesId id) {
        return seriesDataMap.get(id);
    }
}
