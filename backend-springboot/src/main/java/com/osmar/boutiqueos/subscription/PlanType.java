package com.osmar.boutiqueos.subscription;

import java.util.Set;

public enum PlanType {
    BASIC("Boutique OS Básico", Set.of(
        "products", "customers", "sales", "inventory", "categories"
    )),
    PRO("Boutique OS Pro", Set.of(
        "products", "customers", "sales", "inventory", "categories",
        "ticket_customization", "reports", "cash_count", "customer_history",
        "backup", "promotions", "multi_user", "purchases", "refunds"
    ));

    private final String displayName;
    private final Set<String> features;

    PlanType(String displayName, Set<String> features) {
        this.displayName = displayName;
        this.features = features;
    }

    public String getDisplayName() { return displayName; }

    public boolean hasFeature(String feature) {
        return features.contains(feature);
    }
}
