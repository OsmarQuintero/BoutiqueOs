package com.osmar.boutiqueos.settings;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthSessionService {

    public static final String SESSION_HEADER = "X-Boutique-Session";
    private static final Duration SESSION_TTL = Duration.ofHours(12);

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public String createSession(Long accountId) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        sessions.put(token, new SessionInfo(accountId, Instant.now().plus(SESSION_TTL)));
        return token;
    }

    public boolean isValid(String token) {
        return getSession(token) != null;
    }

    public SessionInfo getSession(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        SessionInfo session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return null;
        }
        SessionInfo refreshed = new SessionInfo(session.accountId(), Instant.now().plus(SESSION_TTL));
        sessions.put(token, refreshed);
        return refreshed;
    }

    public void invalidate(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessions.remove(token);
    }
}
