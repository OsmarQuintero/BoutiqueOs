package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta o restablecimiento de la cuenta de dueño, con contraseña elegida por
 * quien tiene el secreto de administracion. Sustituye a las credenciales fijas.
 */
public record OwnerAccountRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 12, max = 72) String password,
        @Size(max = 120) String storeName
) {
}
