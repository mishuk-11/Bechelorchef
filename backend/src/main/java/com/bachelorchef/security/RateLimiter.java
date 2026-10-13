package com.bachelorchef.security;

import com.bachelorchef.config.AppProperties;
import com.bachelorchef.exception.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Simple fixed-window, in-memory rate limiter. It protects a single API instance; when running
 * several replicas, put an edge rate limiter (or a shared store) in front — see docs/deployment.md.
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final Clock clock;

    public RateLimiter(AppProperties properties, Clock clock) {
        this.enabled = properties.rateLimit() == null || properties.rateLimit().enabled();
        this.clock = clock;
    }

    /** Records one hit for the key and throws 429 when more than {@code limit} hits occur in the window. */
    public void check(String key, int limit, Duration window) {
        if (!enabled) {
            return;
        }
        Instant now = clock.instant();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.resetsAt.isBefore(now)) {
                return new Window(now.plus(window), 1);
            }
            return new Window(existing.resetsAt, existing.count + 1);
        });
        if (w.count > limit) {
            throw ApiException.tooManyRequests();
        }
    }

    /** Throws 429 if the key already reached {@code limit} hits in its current window, without recording a hit. */
    public void requireBelow(String key, int limit) {
        if (!enabled) {
            return;
        }
        Window w = windows.get(key);
        if (w != null && !w.resetsAt.isBefore(clock.instant()) && w.count >= limit) {
            throw ApiException.tooManyRequests();
        }
    }

    @Scheduled(fixedDelay = 600_000)
    void evictExpired() {
        Instant now = clock.instant();
        windows.entrySet().removeIf(e -> e.getValue().resetsAt.isBefore(now));
    }

    private record Window(Instant resetsAt, int count) {
    }
}
