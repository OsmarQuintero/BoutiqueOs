package com.osmar.boutiqueos.settings;

/** @param twoFactorAvailable false si la cuenta no tiene correo al cual mandar el codigo. */
public record CredentialsCodeResponse(boolean sent, String maskedEmail, boolean twoFactorAvailable) {
}
