package com.osmar.boutiqueos.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlocklist {

    private static final Logger log = LoggerFactory.getLogger(TokenBlocklist.class);

    private final ConcurrentHashMap<String, Instant> revokedTokens = new ConcurrentHashMap<>();

    public void revoke(String token, Instant expiresAt) {
        if (token != null && !token.isBlank()) {
            revokedTokens.put(token, expiresAt);
            log.debug("Token revocado, expira en: {}", expiresAt);
        }
    }

    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Instant expiresAt = revokedTokens.get(token);
        if (expiresAt == null) {
            return false;
        }
        if (Instant.now().isAfter(expiresAt)) {
            revokedTokens.remove(token);
            return false;
        }
        return true;
    }

    @Scheduled(fixedRate = 3600_000)
    public void cleanup() {
        Instant now = Instant.now();
        int removed = 0;
        var iterator = revokedTokens.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now.isAfter(entry.getValue())) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Blocklist cleanup: removed {} expired tokens", removed);
        }
    }
}
