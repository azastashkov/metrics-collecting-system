package com.metrics.tsdb.server.cache;

import java.time.Duration;
import java.util.Optional;

public interface CacheService {

    Optional<String> get(String key);

    void put(String key, String value, Duration ttl);
}
