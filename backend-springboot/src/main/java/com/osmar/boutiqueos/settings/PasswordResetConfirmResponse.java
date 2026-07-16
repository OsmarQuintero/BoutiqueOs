package com.osmar.boutiqueos.settings;

public record PasswordResetConfirmResponse(
        boolean updated,
        String username
) {
}
