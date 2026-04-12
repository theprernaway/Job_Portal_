package com.jobportal.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * RedisTokenService has two jobs:
 *
 * 1. TOKEN BLACKLIST (logout)
 *    When a user logs out, we store their token in Redis with a TTL
 *    equal to the token's remaining lifetime. Any future request with
 *    that token gets rejected in JwtFilter before reaching any controller.
 *
 * 2. GENERAL CACHE HELPERS
 *    store() / retrieve() / evict() for caching any string value
 *    (e.g. job listings, user profiles) to avoid repeated DB hits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisTokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String CACHE_PREFIX     = "cache:";

    // ── Token blacklist ───────────────────────────────────────────────────────

    /**
     * Called on logout. Stores token in Redis with a TTL.
     * After TTL expires, Redis automatically removes it (no cleanup needed).
     *
     * @param token     the JWT string
     * @param ttlMillis how long until the token would have expired anyway
     */
    public void blacklistToken(String token, long ttlMillis) {
        String key = BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "blacklisted", ttlMillis, TimeUnit.MILLISECONDS);
        log.info("Token blacklisted, TTL={}ms", ttlMillis);
    }

    /**
     * Called in JwtFilter for every request.
     * Returns true if token was previously blacklisted (user logged out).
     */
    public boolean isBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // ── General cache ─────────────────────────────────────────────────────────

    /**
     * Store any string value in Redis with a TTL.
     * Example key: "cache:jobs:all"  value: serialized JSON list
     */
    public void store(String key, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(
            CACHE_PREFIX + key, value, ttlSeconds, TimeUnit.SECONDS
        );
    }

    /**
     * Retrieve a cached value. Returns null if not found or expired.
     */
    public Object retrieve(String key) {
        return redisTemplate.opsForValue().get(CACHE_PREFIX + key);
    }

    /**
     * Remove a cache entry (call this when data changes).
     */
    public void evict(String key) {
        redisTemplate.delete(CACHE_PREFIX + key);
        log.debug("Cache evicted: {}", key);
    }

    /**
     * Remove all entries matching a pattern.
     * Example: evictPattern("jobs:*") clears all job caches.
     */
    public void evictPattern(String pattern) {
        var keys = redisTemplate.keys(CACHE_PREFIX + pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("Evicted {} cache entries matching: {}", keys.size(), pattern);
        }
    }
}
