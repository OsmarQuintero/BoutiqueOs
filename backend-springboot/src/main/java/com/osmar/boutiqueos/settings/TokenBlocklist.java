package com.osmar.boutiqueos.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TokenBlocklist {

    private static final Logger log = LoggerFactory.getLogger(TokenBlocklist.class);

    private final RevokedTokenRepository revokedTokenRepository;

    public TokenBlocklist(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public void revoke(String token, Instant expiresAt) {
        if (token != null && !token.isBlank()) {
            RevokedToken entity = new RevokedToken();
            entity.setToken(token);
            entity.setExpiresAt(expiresAt);
            revokedTokenRepository.save(entity);
            log.debug("Token revocado, expira en: {}", expiresAt);
        }
    }

    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return revokedTokenRepository.findById(token).isPresent();
    }

    @Scheduled(fixedRate = 3600_000)
    public void cleanup() {
        int removed = revokedTokenRepository.deleteExpired(Instant.now());
        if (removed > 0) {
            log.debug("Blocklist cleanup: removed {} expired tokens", removed);
        }
    }
}
