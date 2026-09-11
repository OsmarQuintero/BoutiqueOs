package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param code codigo que llego al correo actual de la cuenta. Es obligatorio si la
 *             cuenta tiene correo: sin el, alguien con una sesion abierta podria
 *             cambiar el correo por el suyo y quedarse con la cuenta.
 */
public record CredentialsSettingsRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 100) String currentPassword,
        @Size(max = 100) String newPassword,
        @Size(max = 12) String code
) {
}
