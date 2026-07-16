package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(@NotBlank String username) {
}
