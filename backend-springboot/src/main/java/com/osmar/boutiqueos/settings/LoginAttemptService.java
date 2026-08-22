package com.osmar.boutiqueos.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final LoginAttemptRepository loginAttemptRepository;

    public LoginAttemptService(LoginAttemptRepository loginAttemptRepository) {
        this.loginAttemptRepository = loginAttemptRepository;
    }

    public boolean isBlocked(String key) {
        return isBlocked(key, MAX_ATTEMPTS, WINDOW, BLOCK_DURATION);
    }

    public boolean isBlocked(String key, int maxAttempts, Duration windowDuration, Duration blockDuration) {
        return loginAttemptRepository.findById(new AttemptKey(key))
                .map(attempt -> {
                    Instant now = Instant.now();
                    if (attempt.getBlockedUntil() != null && attempt.getBlockedUntil().isAfter(now)) {
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    @Transactional
    public void recordFailure(String key) {
        recordFailure(key, MAX_ATTEMPTS, WINDOW, BLOCK_DURATION);
    }

    @Transactional
    public void recordFailure(String key, int maxAttempts, Duration windowDuration, Duration blockDuration) {
        Instant now = Instant.now();
        AttemptKey attemptKey = new AttemptKey(key);
        LoginAttempt attempt = loginAttemptRepository.findById(attemptKey).orElse(null);

        if (attempt == null || attempt.getWindowExpiresAt().isBefore(now)) {
            attempt = new LoginAttempt();
            attempt.setId(attemptKey);
            attempt.setCount(1);
            attempt.setWindowExpiresAt(now.plus(windowDuration));
            attempt.setBlockedUntil(null);
        } else {
            attempt.setCount(attempt.getCount() + 1);
            attempt.setWindowExpiresAt(now.plus(windowDuration));
            if (attempt.getCount() >= maxAttempts) {
                attempt.setBlockedUntil(now.plus(blockDuration));
            }
        }
        loginAttemptRepository.save(attempt);
    }

    @Transactional
    public void reset(String key) {
        loginAttemptRepository.deleteById(new AttemptKey(key));
    }

    @Scheduled(fixedRate = 3600_000)
    public void cleanup() {
        int removed = loginAttemptRepository.deleteExpired(Instant.now());
        if (removed > 0) {
            log.debug("Login attempt cleanup: removed {} expired entries", removed);
        }
    }
}
