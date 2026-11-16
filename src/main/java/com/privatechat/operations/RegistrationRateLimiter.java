package com.privatechat.operations;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
class RegistrationRateLimiter {
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 15 * 60 * 1000L;
    private static final int MAX_KEYS = 4096;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private record Bucket(long startedAt, int attempts) {}

    synchronized boolean allow(String key) {
        long now = System.currentTimeMillis();
        if (buckets.size() >= MAX_KEYS) {
            buckets.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_MS);
            if (buckets.size() >= MAX_KEYS && !buckets.containsKey(key)) return false;
        }
        Bucket current = buckets.get(key);
        if (current == null || now - current.startedAt() >= WINDOW_MS) {
            buckets.put(key, new Bucket(now, 1));
            return true;
        }
        if (current.attempts() >= MAX_ATTEMPTS) return false;
        buckets.put(key, new Bucket(current.startedAt(), current.attempts() + 1));
        return true;
    }
}
