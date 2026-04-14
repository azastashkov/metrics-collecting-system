package com.metrics.loadclient.metrics;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

@Component
public class MetricsRegistry {

    private static final int MAX_RECENT_OBSERVATIONS = 10_000;

    private final LongAdder requestsTotal200 = new LongAdder();
    private final LongAdder requestsTotal500 = new LongAdder();
    private final LongAdder errorsTotal = new LongAdder();
    private final LongAdder durationCount = new LongAdder();
    private final DoubleAdder durationSum = new DoubleAdder();
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<Double> recentDurations = new ConcurrentLinkedQueue<>();

    public void recordRequest(int statusCode, double durationSeconds) {
        if (statusCode == 200) {
            requestsTotal200.increment();
        } else {
            requestsTotal500.increment();
            errorsTotal.increment();
        }
        durationSum.add(durationSeconds);
        durationCount.increment();
        recentDurations.add(durationSeconds);
    }

    public double getQuantile(double q) {
        List<Double> snapshot = new ArrayList<>(recentDurations);
        if (snapshot.isEmpty()) {
            return 0.0;
        }
        Collections.sort(snapshot);
        int index = (int) Math.ceil(q * snapshot.size()) - 1;
        index = Math.max(0, Math.min(index, snapshot.size() - 1));
        return snapshot.get(index);
    }

    public int incrementInFlight() {
        return inFlight.incrementAndGet();
    }

    public int decrementInFlight() {
        return inFlight.decrementAndGet();
    }

    public long getRequestsTotal200() {
        return requestsTotal200.sum();
    }

    public long getRequestsTotal500() {
        return requestsTotal500.sum();
    }

    public long getErrorsTotal() {
        return errorsTotal.sum();
    }

    public long getDurationCount() {
        return durationCount.sum();
    }

    public double getDurationSum() {
        return durationSum.sum();
    }

    public int getInFlight() {
        return inFlight.get();
    }

    @Scheduled(fixedRate = 5000)
    public void trimRecentDurations() {
        int excess = recentDurations.size() - MAX_RECENT_OBSERVATIONS;
        for (int i = 0; i < excess; i++) {
            recentDurations.poll();
        }
    }
}
