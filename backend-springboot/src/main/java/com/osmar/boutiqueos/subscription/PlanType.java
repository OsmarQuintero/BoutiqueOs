package com.osmar.boutiqueos.subscription;

import java.util.Set;

/**
 * Que incluye cada plan.
 *
 * <p>Basico = todo lo necesario para operar una boutique con una sola persona
 * (vender, devolver, apartar, cortar caja, respaldar). Pro = equipo y crecimiento:
 * usuarias de caja, reportes por periodo, promociones y puntos, historial de
 * clientas, compras a proveedores y ticket con marca propia.
 */
public enum PlanType {
    BASIC("Boutique OS Básico", 0, Set.of(
        "products", "customers", "sales", "inventory", "categories",
        "cash_count", "refunds", "layaways", "backup"
    )),
    PRO("Boutique OS Pro", 3, Set.of(
        "products", "customers", "sales", "inventory", "categories",
        "cash_count", "refunds", "layaways", "backup",
        "ticket_customization", "reports", "customer_history",
        "promotions", "multi_user", "purchases"
    ));

    private final String displayName;
    private final int maxStaffUsers;
    private final Set<String> features;

    PlanType(String displayName, int maxStaffUsers, Set<String> features) {
        this.displayName = displayName;
        this.maxStaffUsers = maxStaffUsers;
        this.features = features;
    }

    public String getDisplayName() { return displayName; }

    /** Usuarias de caja activas que incluye el plan (la duena no cuenta). */
    public int getMaxStaffUsers() { return maxStaffUsers; }

    public boolean hasFeature(String feature) {
        return features.contains(feature);
    }
}
