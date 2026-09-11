package com.osmar.boutiqueos.settings;

/**
 * Respuesta del login.
 *
 * <ul>
 *   <li>{@code valid=false}: usuario o contrasena incorrectos.</li>
 *   <li>{@code twoFactorRequired=true}: la contrasena es correcta, falta el codigo
 *       que se mando a {@code maskedEmail}; se confirma con {@code challengeId}.</li>
 *   <li>{@code token} con valor: ya entro. {@code deviceToken} viene solo si pidio
 *       recordar el dispositivo.</li>
 *   <li>{@code twoFactorAvailable=false}: la cuenta no tiene correo, asi que entro
 *       sin segundo paso; la pantalla le pide agregar uno.</li>
 * </ul>
 */
public record LoginResponse(
        boolean valid,
        String token,
        boolean twoFactorRequired,
        String challengeId,
        String maskedEmail,
        String deviceToken,
        boolean twoFactorAvailable,
        String role,
        String displayName,
        boolean codeSentToOwner
) {

    public LoginResponse(boolean valid, String token) {
        this(valid, token, false, null, null, null, true, null, null, false);
    }

    public static LoginResponse invalid() {
        return new LoginResponse(false, null, false, null, null, null, true, null, null, false);
    }

    /** @param codeSentToOwner true si entra una cajera: el codigo le llego a la duena. */
    public static LoginResponse challenge(String challengeId, String maskedEmail, boolean codeSentToOwner) {
        return new LoginResponse(true, null, true, challengeId, maskedEmail, null, true, null, null, codeSentToOwner);
    }

    public static LoginResponse signedIn(String token, String deviceToken, boolean twoFactorAvailable,
                                         String role, String displayName) {
        return new LoginResponse(true, token, false, null, null, deviceToken, twoFactorAvailable, role, displayName, false);
    }
}
