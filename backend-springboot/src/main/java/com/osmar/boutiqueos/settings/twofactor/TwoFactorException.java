package com.osmar.boutiqueos.settings.twofactor;

/**
 * Codigo invalido, vencido o agotado.
 *
 * <p>Es su propia clase para poder marcarla como "no revierte la transaccion": el
 * contador de intentos fallidos tiene que guardarse aunque el codigo sea
 * incorrecto. Si se revirtiera, el contador nunca subiria y se podrian probar
 * los 1,000,000 de codigos sin limite. Hereda de IllegalArgumentException para que
 * el manejador global la responda como 400 con su mensaje.
 */
public class TwoFactorException extends IllegalArgumentException {
    public TwoFactorException(String message) {
        super(message);
    }
}
