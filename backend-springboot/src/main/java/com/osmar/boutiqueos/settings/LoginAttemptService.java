package com.osmar.boutiqueos.settings;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        return isBlocked(key, MAX_ATTEMPTS, WINDOW, BLOCK_DURATION);
    }

    public boolean isBlocked(String key, int maxAttempts, Duration windowDuration, Duration blockDuration) {
        AttemptWindow window = attempts.get(key);
        if (window == null) {
            return false;
        }
        Instant now = Instant.now();
        if (window.blockedUntil != null && window.blockedUntil.isAfter(now)) {
            return true;
        }
        if (window.blockedUntil != null && !window.blockedUntil.isAfter(now) && window.expiresAt.isBefore(now)) {
            attempts.remove(key);
        }
        return false;
    }

    public void recordFailure(String key) {
        recordFailure(key, MAX_ATTEMPTS, WINDOW, BLOCK_DURATION);
    }

    public void recordFailure(String key, int maxAttempts, Duration windowDuration, Duration blockDuration) {
        Instant now = Instant.now();
        attempts.compute(key, (ignored, existing) -> {
            AttemptWindow window = existing;
            if (window == null || window.expiresAt.isBefore(now)) {
                window = new AttemptWindow(0, now.plus(windowDuration), null);
            }

            int nextCount = window.count + 1;
            Instant blockedUntil = nextCount >= maxAttempts ? now.plus(blockDuration) : window.blockedUntil;
            return new AttemptWindow(nextCount, now.plus(windowDuration), blockedUntil);
        });
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    private record AttemptWindow(int count, Instant expiresAt, Instant blockedUntil) {
    }
}
