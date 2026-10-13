package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.exception.AiServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-user fixed-window limiter. In-memory, so each application instance enforces its own limit. */
@Component
public class AiRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int CLEANUP_THRESHOLD = 1000;

    private record Window(Instant start, int count) {
    }

    private final ConcurrentHashMap<UUID, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int limitPerWindow;

    public AiRateLimiter(Clock clock, @Value("${app.ai.rate-limit.requests-per-minute:10}") int limitPerWindow) {
        if (limitPerWindow < 1) {
            throw new IllegalStateException("Configuration error: app.ai.rate-limit.requests-per-minute must be at least 1");
        }
        this.clock = clock;
        this.limitPerWindow = limitPerWindow;
    }

    public void acquire(UUID userId) {
        Instant now = clock.instant();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> !w.start().plus(WINDOW).isAfter(now));
        }
        Window updated = windows.compute(userId, (id, current) -> {
            if (current == null || !current.start().plus(WINDOW).isAfter(now)) {
                return new Window(now, 1);
            }
            return new Window(current.start(), current.count() + 1);
        });
        if (updated.count() > limitPerWindow) {
            throw new AiServiceException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many AI requests; please wait a minute and try again");
        }
    }
}
