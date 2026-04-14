package com.metrics.tsdb.storage.partition;

import com.metrics.tsdb.model.Sample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SeriesData {

    private static final int INITIAL_CAPACITY = 256;

    private long[] timestamps;
    private double[] values;
    private int size;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public SeriesData() {
        this.timestamps = new long[INITIAL_CAPACITY];
        this.values = new double[INITIAL_CAPACITY];
        this.size = 0;
    }

    public SeriesData(long[] timestamps, double[] values, int size) {
        this.timestamps = Arrays.copyOf(timestamps, Math.max(timestamps.length, size));
        this.values = Arrays.copyOf(values, Math.max(values.length, size));
        this.size = size;
    }

    public void append(long timestamp, double value) {
        lock.writeLock().lock();
        try {
            if (size > 0 && timestamp < timestamps[size - 1]) {
                return; // Drop out-of-order samples
            }
            ensureCapacity();
            timestamps[size] = timestamp;
            values[size] = value;
            size++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Sample> range(long startMs, long endMs) {
        lock.readLock().lock();
        try {
            if (size == 0) {
                return List.of();
            }

            int startIdx = findFirstGe(startMs);
            int endIdx = findLastLe(endMs);

            if (startIdx > endIdx || startIdx >= size) {
                return List.of();
            }

            List<Sample> result = new ArrayList<>(endIdx - startIdx + 1);
            for (int i = startIdx; i <= endIdx; i++) {
                result.add(new Sample(timestamps[i], values[i]));
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return size;
        } finally {
            lock.readLock().unlock();
        }
    }

    public long[] getTimestampsCopy() {
        lock.readLock().lock();
        try {
            return Arrays.copyOf(timestamps, size);
        } finally {
            lock.readLock().unlock();
        }
    }

    public double[] getValuesCopy() {
        lock.readLock().lock();
        try {
            return Arrays.copyOf(values, size);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void ensureCapacity() {
        if (size >= timestamps.length) {
            int newCapacity = timestamps.length * 2;
            timestamps = Arrays.copyOf(timestamps, newCapacity);
            values = Arrays.copyOf(values, newCapacity);
        }
    }

    private int findFirstGe(long target) {
        int lo = 0, hi = size;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (timestamps[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private int findLastLe(long target) {
        int lo = 0, hi = size;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (timestamps[mid] <= target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo - 1;
    }
}
