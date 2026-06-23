package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;

public record CredentialsSettingsRequest(
        @NotBlank String username,
        @NotBlank String currentPassword,
        String newPassword
) {
}
