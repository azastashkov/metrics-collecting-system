package com.metrics.tsdb.server.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "tsdb.cache.enabled", havingValue = "false")
public class NoOpCacheService implements CacheService {

    @Override
    public Optional<String> get(String key) {
        return Optional.empty();
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        // no-op
    }
}
