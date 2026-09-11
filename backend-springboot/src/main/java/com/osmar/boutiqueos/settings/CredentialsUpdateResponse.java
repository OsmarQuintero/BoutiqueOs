package com.osmar.boutiqueos.settings;

/**
 * @param token sesion nueva. Al cambiar la contrasena se cierran todas las sesiones
 *              abiertas, incluida la de quien hizo el cambio; esta la mantiene dentro.
 */
public record CredentialsUpdateResponse(AppSettingsResponse settings, String token) {
}
