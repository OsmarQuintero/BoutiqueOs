package com.osmar.boutiqueos.settings;

import java.time.Instant;

/**
 * @param issuedAt cuando se emitio el token. Sirve para invalidar las sesiones
 *                 abiertas antes de un cambio de contrasena.
 */
public record SessionInfo(Long accountId, Instant expiresAt, Instant issuedAt, Long staffUserId) {

    public SessionInfo(Long accountId, Instant expiresAt) {
        this(accountId, expiresAt, null, null);
    }

    public SessionInfo(Long accountId, Instant expiresAt, Instant issuedAt) {
        this(accountId, expiresAt, issuedAt, null);
    }

    /** null = la duena; con valor = una cuenta de caja. */
    public boolean isStaff() {
        return staffUserId != null;
    }
}
