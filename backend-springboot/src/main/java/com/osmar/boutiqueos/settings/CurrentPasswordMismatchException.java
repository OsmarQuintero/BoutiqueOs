package com.osmar.boutiqueos.settings;

/**
 * La contrasena actual no coincide. Clase propia para que el controlador pueda
 * contar el intento fallido (SEC-04) sin depender del texto del mensaje. Hereda de
 * IllegalArgumentException para responder 400 con su mensaje.
 */
public class CurrentPasswordMismatchException extends IllegalArgumentException {
    public CurrentPasswordMismatchException() {
        super("La contrasena actual no es correcta");
    }
}
