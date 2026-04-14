package com.metrics.tsdb.storage.retention;

import com.metrics.tsdb.storage.partition.PartitionManager;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RetentionManager {

    private final PartitionManager partitionManager;
    private final RetentionPolicy policy;
    private final Clock clock;
    private ScheduledExecutorService executor;

    public RetentionManager(PartitionManager partitionManager, RetentionPolicy policy, Clock clock) {
        this.partitionManager = partitionManager;
        this.policy = policy;
        this.clock = clock;
    }

    public RetentionManager(PartitionManager partitionManager, RetentionPolicy policy) {
        this(partitionManager, policy, Clock.systemUTC());
    }

    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tsdb-retention");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::runOnce, 5, 5, TimeUnit.MINUTES);
    }

    public void runOnce() {
        long cutoff = clock.millis() - policy.getRetention().toMillis();
        var partitions = partitionManager.getPartitions();

        for (var entry : partitions.entrySet()) {
            if (entry.getValue().getEndTimeMs() <= cutoff) {
                partitionManager.removePartition(entry.getKey());
            }
        }
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
