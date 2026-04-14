package com.metrics.tsdb.storage.partition;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PartitionManager {

    private static final Logger LOG = Logger.getLogger(PartitionManager.class.getName());

    private final ConcurrentSkipListMap<Long, TimePartition> partitions = new ConcurrentSkipListMap<>();
    private final Path dataDirectory;
    private final long partitionDurationMs;
    private ScheduledExecutorService flushExecutor;

    public PartitionManager(Path dataDirectory, long partitionDurationMs) {
        this.dataDirectory = dataDirectory;
        this.partitionDurationMs = partitionDurationMs;
    }

    public TimePartition getPartitionForTimestamp(long timestamp) {
        long startTime = alignToPartition(timestamp);
        return partitions.computeIfAbsent(startTime,
                st -> new TimePartition(st, partitionDurationMs));
    }

    public List<TimePartition> getPartitionsForRange(long startMs, long endMs) {
        long alignedStart = alignToPartition(startMs);
        long alignedEnd = alignToPartition(endMs);

        List<TimePartition> result = new ArrayList<>();
        var subMap = partitions.subMap(alignedStart, true, alignedEnd, true);
        result.addAll(subMap.values());
        return result;
    }

    public void flush(TimePartition partition) {
        if (dataDirectory == null) return;
        try {
            PartitionDiskIO.write(partition, dataDirectory, partitionDurationMs);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to flush partition", e);
        }
    }

    public void loadFromDisk() {
        if (dataDirectory == null || !Files.exists(dataDirectory)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDirectory, "partition_*.bin")) {
            for (Path file : stream) {
                try {
                    TimePartition partition = PartitionDiskIO.read(file, partitionDurationMs);
                    partitions.put(partition.getStartTimeMs(), partition);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to load partition file: " + file, e);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to scan partition directory", e);
        }
    }

    public void removePartition(long startTimeMs) {
        partitions.remove(startTimeMs);
        if (dataDirectory != null) {
            try {
                Path file = dataDirectory.resolve("partition_" + startTimeMs + ".bin");
                Files.deleteIfExists(file);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to delete partition file", e);
            }
        }
    }

    public void start() {
        flushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tsdb-flush");
            t.setDaemon(true);
            return t;
        });
        flushExecutor.scheduleAtFixedRate(this::flushCompleted, 30, 30, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (flushExecutor != null) {
            flushExecutor.shutdown();
        }
        // Flush all partitions on shutdown
        for (TimePartition partition : partitions.values()) {
            flush(partition);
        }
    }

    public ConcurrentSkipListMap<Long, TimePartition> getPartitions() {
        return partitions;
    }

    private void flushCompleted() {
        long now = System.currentTimeMillis();
        for (var entry : partitions.entrySet()) {
            if (entry.getValue().isComplete(now)) {
                flush(entry.getValue());
            }
        }
    }

    private long alignToPartition(long timestamp) {
        return (timestamp / partitionDurationMs) * partitionDurationMs;
    }
}
