package com.metrics.tsdb.server.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "tsdb.cache.enabled", havingValue = "true", matchIfMissing = true)
public class RedisCacheService implements CacheService {

    private final StringRedisTemplate redisTemplate;

    public RedisCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> get(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(value);
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public static String cacheKey(String query, long alignedStart, long alignedEnd, String step) {
        String raw = query + ":" + alignedStart + ":" + alignedEnd + ":" + step;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
