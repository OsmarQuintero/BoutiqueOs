package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Recuperacion con el codigo que llego al correo (antes era un enlace con token). */
public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 12) String code,
        @NotBlank @Size(min = 12, max = 72) String newPassword
) {
}
