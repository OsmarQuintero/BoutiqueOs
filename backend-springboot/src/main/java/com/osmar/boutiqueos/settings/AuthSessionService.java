package com.osmar.boutiqueos.settings;

import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {

    public static final String SESSION_HEADER = "X-Boutique-Session";

    private final JwtTokenService jwtTokenService;

    public AuthSessionService(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    public String createSession(Long accountId) {
        return jwtTokenService.createToken(accountId);
    }

    public boolean isValid(String token) {
        return getSession(token) != null;
    }

    public SessionInfo getSession(String token) {
        return jwtTokenService.parseToken(token);
    }

    public void invalidate(String token) {
        // JWT stateless: no hay revocacion en servidor.
        // El cliente descarta el token; la expiracion se valida por firma y fecha.
    }
}
