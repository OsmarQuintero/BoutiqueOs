package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;

/**
 * Restaurar borra todo lo que hay en la cuenta, asi que no basta con llamar al
 * endpoint: hay que mandar en {@code confirmation} el nombre de la tienda tal
 * como esta guardado. La pantalla tambien lo pide, pero la regla vive aqui para
 * que una llamada directa a la API tampoco pueda borrar por accidente.
 */
public record BackupRestoreRequest(
        @NotBlank(message = "Escribe el nombre de la tienda para confirmar")
        String confirmation,
        BackupPayload backup
) {
}
