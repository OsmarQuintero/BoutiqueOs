package com.osmar.boutiqueos.subscription;

import java.util.Locale;

/** Cada cuanto se cobra la suscripcion. */
public enum BillingInterval {
    MONTHLY,
    /** Pago anual: equivale a 10 meses (2 gratis). */
    ANNUAL;

    /** "annual", "anual", "year", "yearly" -> ANNUAL; cualquier otra cosa -> MONTHLY. */
    public static BillingInterval parse(String value) {
        if (value == null) {
            return MONTHLY;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "annual", "anual", "year", "yearly" -> ANNUAL;
            default -> MONTHLY;
        };
    }
}
