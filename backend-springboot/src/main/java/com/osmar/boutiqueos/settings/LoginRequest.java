package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param deviceToken token de "recordar este dispositivo"; si es valido para la
 *                    cuenta, no se pide codigo por correo.
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        @Size(max = 200) String deviceToken
) {

    public LoginRequest(String username, String password) {
        this(username, password, null);
    }
}
