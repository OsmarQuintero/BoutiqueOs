package com.osmar.boutiqueos.settings;

import org.springframework.beans.factory.annotation.Autowired;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final Duration ttl;
    private final SecretKey key;

    @Autowired
    public JwtTokenService(
            @Value("${app.jwt.secret:}") String configuredSecret,
            @Value("${app.jwt.ttl-hours:12}") long ttlHours,
            @Value("${app.jwt.require-secret:false}") boolean requireSecret
    ) {
        this.ttl = Duration.ofHours(ttlHours);
        this.key = buildKey(configuredSecret, requireSecret);
    }

    /** Para pruebas: sin exigir la clave. */
    public JwtTokenService(String configuredSecret, long ttlHours) {
        this(configuredSecret, ttlHours, false);
    }

    public String createToken(Long accountId) {
        return createToken(accountId, null);
    }

    /** @param staffUserId cuenta de caja; null para la duena. */
    public String createToken(Long accountId, Long staffUserId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(accountId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        if (staffUserId != null) {
            builder = builder.claim("uid", staffUserId);
        }
        return builder.signWith(key).compact();
    }

    public SessionInfo parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long accountId = Long.valueOf(claims.getSubject());
            Instant expiresAt = claims.getExpiration().toInstant();
            Instant issuedAt = claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant();
            Object uid = claims.get("uid");
            Long staffUserId = uid == null ? null : Long.valueOf(String.valueOf(uid));
            return new SessionInfo(accountId, expiresAt, issuedAt, staffUserId);
        } catch (Exception exception) {
            return null;
        }
    }

    private SecretKey buildKey(String configuredSecret, boolean requireSecret) {
        String secret = configuredSecret == null ? "" : configuredSecret.trim();
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            if (requireSecret) {
                // En produccion es preferible no arrancar que sacar a todas las
                // clientas de su sesion en cada reinicio o deploy.
                throw new IllegalStateException(
                        "APP_JWT_SECRET es obligatoria (APP_JWT_REQUIRE_SECRET=true) y debe tener al menos "
                        + MIN_SECRET_BYTES + " caracteres");
            }
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            log.warn(
                    "APP_JWT_SECRET no esta configurada o es demasiado corta. "
                    + "Se genero una clave aleatoria: las sesiones se invalidaran al reiniciar. "
                    + "Configura APP_JWT_SECRET en produccion."
            );
            return Keys.hmacShaKeyFor(random);
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
