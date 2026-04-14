package com.metrics.tsdb.storage;

import com.metrics.tsdb.model.SeriesId;
import com.metrics.tsdb.model.SeriesKey;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SeriesIndex {

    private final ConcurrentHashMap<SeriesKey, SeriesId> index = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public SeriesId getOrCreate(SeriesKey key) {
        return index.computeIfAbsent(key, k -> new SeriesId(nextId.getAndIncrement()));
    }

    public SeriesId get(SeriesKey key) {
        return index.get(key);
    }

    public Set<SeriesKey> findByMetricName(String name) {
        return index.keySet().stream()
                .filter(k -> k.getMetricName().equals(name))
                .collect(Collectors.toSet());
    }

    public Set<SeriesKey> match(String metricName, Map<String, String> matchers) {
        return findByMetricName(metricName).stream()
                .filter(key -> matchesAllLabels(key, matchers))
                .collect(Collectors.toSet());
    }

    public Set<SeriesKey> matchWithOps(String metricName, java.util.List<LabelMatcher> matchers) {
        return findByMetricName(metricName).stream()
                .filter(key -> matchesAllOps(key, matchers))
                .collect(Collectors.toSet());
    }

    public Set<SeriesKey> getAllKeys() {
        return Set.copyOf(index.keySet());
    }

    private boolean matchesAllLabels(SeriesKey key, Map<String, String> matchers) {
        for (Map.Entry<String, String> entry : matchers.entrySet()) {
            String labelVal = key.getLabels().get(entry.getKey());
            if (labelVal == null || !labelVal.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAllOps(SeriesKey key, java.util.List<LabelMatcher> matchers) {
        for (LabelMatcher matcher : matchers) {
            String labelVal = key.getLabels().get(matcher.name());
            if (!matchLabel(labelVal, matcher)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchLabel(String labelVal, LabelMatcher matcher) {
        if (labelVal == null) {
            labelVal = "";
        }
        return switch (matcher.type()) {
            case EQUAL -> labelVal.equals(matcher.value());
            case NOT_EQUAL -> !labelVal.equals(matcher.value());
            case REGEX_MATCH -> Pattern.matches(matcher.value(), labelVal);
            case REGEX_NOT_MATCH -> !Pattern.matches(matcher.value(), labelVal);
        };
    }

    public record LabelMatcher(String name, MatchType type, String value) {
        public enum MatchType {
            EQUAL, NOT_EQUAL, REGEX_MATCH, REGEX_NOT_MATCH
        }
    }
}
