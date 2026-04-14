package com.metrics.common.parser;

import com.metrics.common.model.MetricSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PrometheusTextParser {

    private PrometheusTextParser() {
    }

    public static List<MetricSample> parse(String expositionText, long scrapeTimestampMs) {
        if (expositionText == null || expositionText.isBlank()) {
            return Collections.emptyList();
        }

        List<MetricSample> samples = new ArrayList<>();
        String[] lines = expositionText.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            try {
                MetricSample sample = parseLine(trimmed, scrapeTimestampMs);
                if (sample != null) {
                    samples.add(sample);
                }
            } catch (Exception e) {
                // Skip malformed lines
            }
        }

        return samples;
    }

    private static MetricSample parseLine(String line, long scrapeTimestampMs) {
        String metricName;
        Map<String, String> labels;
        double value;

        int braceOpen = line.indexOf('{');
        int braceClose = line.indexOf('}');

        if (braceOpen >= 0 && braceClose > braceOpen) {
            metricName = line.substring(0, braceOpen).trim();
            String labelsStr = line.substring(braceOpen + 1, braceClose);
            labels = parseLabels(labelsStr);
            String rest = line.substring(braceClose + 1).trim();
            value = parseValue(rest);
        } else {
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                return null;
            }
            metricName = parts[0];
            labels = Collections.emptyMap();
            value = Double.parseDouble(parts[1]);
        }

        return MetricSample.builder()
                .name(metricName)
                .labels(labels)
                .value(value)
                .timestamp(scrapeTimestampMs)
                .build();
    }

    private static Map<String, String> parseLabels(String labelsStr) {
        if (labelsStr == null || labelsStr.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, String> labels = new LinkedHashMap<>();
        int i = 0;
        while (i < labelsStr.length()) {
            // Skip whitespace and commas
            while (i < labelsStr.length() && (labelsStr.charAt(i) == ',' || labelsStr.charAt(i) == ' ')) {
                i++;
            }
            if (i >= labelsStr.length()) break;

            // Find '='
            int eqIdx = labelsStr.indexOf('=', i);
            if (eqIdx < 0) break;

            String key = labelsStr.substring(i, eqIdx).trim();

            // Find quoted value
            int quoteStart = labelsStr.indexOf('"', eqIdx);
            if (quoteStart < 0) break;

            int quoteEnd = findClosingQuote(labelsStr, quoteStart + 1);
            if (quoteEnd < 0) break;

            String val = labelsStr.substring(quoteStart + 1, quoteEnd);
            labels.put(key, val);
            i = quoteEnd + 1;
        }

        return labels;
    }

    private static int findClosingQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                return i;
            }
        }
        return -1;
    }

    private static double parseValue(String rest) {
        String[] parts = rest.trim().split("\\s+");
        return Double.parseDouble(parts[0]);
    }
}
