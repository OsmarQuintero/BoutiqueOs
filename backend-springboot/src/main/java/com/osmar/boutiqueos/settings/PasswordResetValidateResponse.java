package com.osmar.boutiqueos.settings;

public record PasswordResetValidateResponse(
        boolean valid,
        String email,
        String expiresAt
) {
}
