package com.metrics.consumer.batch;

import com.metrics.common.model.MetricSample;
import com.metrics.consumer.client.TsdbWriteClient;
import com.metrics.consumer.config.ConsumerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class BatchAccumulator {

    private final ConsumerProperties properties;
    private final TsdbWriteClient tsdbWriteClient;

    private final ReentrantLock lock = new ReentrantLock();
    private ArrayList<MetricSample> buffer = new ArrayList<>();
    private long lastFlushTime = System.currentTimeMillis();

    public BatchAccumulator(ConsumerProperties properties, TsdbWriteClient tsdbWriteClient) {
        this.properties = properties;
        this.tsdbWriteClient = tsdbWriteClient;
    }

    public void add(MetricSample sample) {
        lock.lock();
        try {
            buffer.add(sample);
            if (buffer.size() >= properties.getBatchSize()) {
                flush();
            }
        } finally {
            lock.unlock();
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void checkTimeBasedFlush() {
        lock.lock();
        try {
            long elapsed = System.currentTimeMillis() - lastFlushTime;
            if (!buffer.isEmpty() && elapsed >= properties.getFlushIntervalMs()) {
                flush();
            }
        } finally {
            lock.unlock();
        }
    }

    private void flush() {
        List<MetricSample> batch = buffer;
        buffer = new ArrayList<>();
        lastFlushTime = System.currentTimeMillis();

        log.info("Flushing batch of {} samples to TSDB", batch.size());
        tsdbWriteClient.write(batch);
    }
}
