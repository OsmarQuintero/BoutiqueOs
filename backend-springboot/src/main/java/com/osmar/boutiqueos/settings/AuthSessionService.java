package com.osmar.boutiqueos.settings;

import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {

    public static final String SESSION_HEADER = "X-Boutique-Session";

    private final JwtTokenService jwtTokenService;
    private final TokenBlocklist tokenBlocklist;

    public AuthSessionService(JwtTokenService jwtTokenService, TokenBlocklist tokenBlocklist) {
        this.jwtTokenService = jwtTokenService;
        this.tokenBlocklist = tokenBlocklist;
    }

    public String createSession(Long accountId) {
        return jwtTokenService.createToken(accountId);
    }

    public boolean isValid(String token) {
        return getSession(token) != null;
    }

    public SessionInfo getSession(String token) {
        if (tokenBlocklist.isRevoked(token)) {
            return null;
        }
        return jwtTokenService.parseToken(token);
    }

    public void invalidate(String token) {
        if (token != null && !token.isBlank()) {
            SessionInfo info = jwtTokenService.parseToken(token);
            if (info != null) {
                tokenBlocklist.revoke(token, info.expiresAt());
            }
        }
    }
}
