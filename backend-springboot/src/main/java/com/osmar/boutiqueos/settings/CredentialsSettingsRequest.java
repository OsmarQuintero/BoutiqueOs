package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CredentialsSettingsRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 100) String currentPassword,
        @Size(max = 100) String newPassword
) {
}
