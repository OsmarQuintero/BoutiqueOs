package com.osmar.boutiqueos.settings;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtTokenServiceTests {

    private final JwtTokenService jwtTokenService = new JwtTokenService("test-secret-that-is-long-enough-for-hs256", 1);

    @Test
    void createsAndParsesTokenForAccount() {
        String token = jwtTokenService.createToken(42L);

        SessionInfo session = jwtTokenService.parseToken(token);

        assertNotNull(session);
        assertEquals(42L, session.accountId());
        assertNotNull(session.expiresAt());
        assert session.expiresAt().isAfter(Instant.now());
    }

    @Test
    void rejectsNullAndBlankTokens() {
        assertNull(jwtTokenService.parseToken(null));
        assertNull(jwtTokenService.parseToken("  "));
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtTokenService.createToken(7L);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertNull(jwtTokenService.parseToken(tampered));
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtTokenService other = new JwtTokenService("a-different-secret-that-is-also-long-enough", 1);
        String token = other.createToken(9L);

        assertNull(jwtTokenService.parseToken(token));
    }
}
