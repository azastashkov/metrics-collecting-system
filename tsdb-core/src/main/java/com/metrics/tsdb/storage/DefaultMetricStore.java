package com.metrics.tsdb.storage;

import com.metrics.tsdb.model.Sample;
import com.metrics.tsdb.model.SeriesId;
import com.metrics.tsdb.model.SeriesKey;
import com.metrics.tsdb.model.TimeSeries;
import com.metrics.tsdb.storage.partition.PartitionManager;
import com.metrics.tsdb.storage.partition.TimePartition;
import com.metrics.tsdb.storage.retention.RetentionManager;
import com.metrics.tsdb.storage.retention.RetentionPolicy;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultMetricStore implements MetricStore {

    private final SeriesIndex seriesIndex;
    private final PartitionManager partitionManager;
    private final RetentionManager retentionManager;

    public DefaultMetricStore(MetricStoreConfig config) {
        this(config, Clock.systemUTC());
    }

    public DefaultMetricStore(MetricStoreConfig config, Clock clock) {
        this.seriesIndex = new SeriesIndex();
        this.partitionManager = new PartitionManager(
                config.getDataDirectory(),
                config.getPartitionDuration().toMillis());
        this.retentionManager = new RetentionManager(
                partitionManager,
                new RetentionPolicy(config.getRetention()),
                clock);
    }

    @Override
    public void write(String metricName, Map<String, String> labels, double value, long timestamp) {
        SeriesKey key = new SeriesKey(metricName, labels);
        SeriesId id = seriesIndex.getOrCreate(key);
        TimePartition partition = partitionManager.getPartitionForTimestamp(timestamp);
        partition.write(id, timestamp, value);
    }

    @Override
    public List<TimeSeries> query(String metricName, Map<String, String> matchers, long startMs, long endMs) {
        Set<SeriesKey> matchingKeys = seriesIndex.match(metricName, matchers);
        return querySeries(matchingKeys, startMs, endMs);
    }

    @Override
    public List<TimeSeries> queryWithOps(String metricName, List<SeriesIndex.LabelMatcher> matchers, long startMs, long endMs) {
        Set<SeriesKey> matchingKeys = seriesIndex.matchWithOps(metricName, matchers);
        return querySeries(matchingKeys, startMs, endMs);
    }

    @Override
    public Set<String> labelValues(String labelName) {
        return seriesIndex.getAllKeys().stream()
                .map(k -> k.getLabels().get(labelName))
                .filter(v -> v != null)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> metricNames() {
        return seriesIndex.getAllKeys().stream()
                .map(SeriesKey::getMetricName)
                .collect(Collectors.toSet());
    }

    @Override
    public void start() {
        partitionManager.loadFromDisk();
        partitionManager.start();
        retentionManager.start();
    }

    @Override
    public void shutdown() {
        retentionManager.shutdown();
        partitionManager.shutdown();
    }

    private List<TimeSeries> querySeries(Set<SeriesKey> matchingKeys, long startMs, long endMs) {
        List<TimePartition> partitions = partitionManager.getPartitionsForRange(startMs, endMs);
        List<TimeSeries> result = new ArrayList<>();

        for (SeriesKey key : matchingKeys) {
            SeriesId id = seriesIndex.get(key);
            if (id == null) continue;

            List<Sample> allSamples = new ArrayList<>();
            for (TimePartition partition : partitions) {
                allSamples.addAll(partition.read(id, startMs, endMs));
            }

            if (!allSamples.isEmpty()) {
                result.add(TimeSeries.builder()
                        .key(key)
                        .samples(Collections.unmodifiableList(allSamples))
                        .build());
            }
        }

        return result;
    }
}
