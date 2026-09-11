package com.osmar.boutiqueos.config;

import org.springframework.stereotype.Component;

/**
 * Quien hace la peticion actual (la cuenta de la tienda la guarda AccountContext).
 *
 * <p>Si nadie lo fijo (pruebas que llaman servicios directo, tareas programadas),
 * se asume la duena: es el comportamiento que tenia el sistema antes de que
 * existieran las cuentas de caja.
 */
@Component
public class CurrentUser {

    public record Info(UserRole role, Long staffUserId, String displayName, Integer maxDiscountPercent) {
    }

    private static final Info OWNER = new Info(UserRole.OWNER, null, "Dueña", null);

    private final ThreadLocal<Info> holder = new ThreadLocal<>();

    public void set(Info info) {
        holder.set(info);
    }

    public Info get() {
        Info info = holder.get();
        return info == null ? OWNER : info;
    }

    public boolean isCashier() {
        return get().role() == UserRole.CASHIER;
    }

    public void clear() {
        holder.remove();
    }
}
