package com.metrics.tsdb.evaluator.function;

import com.metrics.tsdb.model.Sample;

import java.time.Duration;
import java.util.List;

public final class RateFunctions {

    private RateFunctions() {
    }

    public static double rate(List<Sample> samples, Duration range) {
        if (samples.size() < 2) {
            return 0.0;
        }

        double totalIncrease = computeIncrease(samples);
        double rangeSeconds = range.toMillis() / 1000.0;
        return totalIncrease / rangeSeconds;
    }

    public static double irate(List<Sample> samples) {
        if (samples.size() < 2) {
            return 0.0;
        }

        int last = samples.size() - 1;
        double valueDiff = samples.get(last).getValue() - samples.get(last - 1).getValue();
        double timeDiffSec = (samples.get(last).getTimestamp() - samples.get(last - 1).getTimestamp()) / 1000.0;

        if (timeDiffSec <= 0) return 0.0;

        // Handle counter reset
        if (valueDiff < 0) {
            valueDiff = samples.get(last).getValue();
        }

        return valueDiff / timeDiffSec;
    }

    public static double increase(List<Sample> samples) {
        if (samples.size() < 2) {
            return 0.0;
        }
        return computeIncrease(samples);
    }

    private static double computeIncrease(List<Sample> samples) {
        double totalIncrease = 0;
        for (int i = 1; i < samples.size(); i++) {
            double diff = samples.get(i).getValue() - samples.get(i - 1).getValue();
            if (diff < 0) {
                // Counter reset: add the new value (assume reset to 0)
                totalIncrease += samples.get(i).getValue();
            } else {
                totalIncrease += diff;
            }
        }
        return totalIncrease;
    }
}
