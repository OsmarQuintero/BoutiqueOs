package com.osmar.boutiqueos.subscription;

public enum PlanType {
    BASIC("Boutique OS Básico", 1000, 2000, 10000, 50),
    PRO("Boutique OS Pro", -1, -1, -1, -1);

    private final String displayName;
    private final int maxProducts;
    private final int maxCustomers;
    private final int maxSalesPerMonth;
    private final int maxMonthlyRevenue;

    PlanType(String displayName, int maxProducts, int maxCustomers, int maxSalesPerMonth, int maxMonthlyRevenue) {
        this.displayName = displayName;
        this.maxProducts = maxProducts;
        this.maxCustomers = maxCustomers;
        this.maxSalesPerMonth = maxSalesPerMonth;
        this.maxMonthlyRevenue = maxMonthlyRevenue;
    }

    public String getDisplayName() { return displayName; }
    public int getMaxProducts() { return maxProducts; }
    public int getMaxCustomers() { return maxCustomers; }
    public int getMaxSalesPerMonth() { return maxSalesPerMonth; }
    public int getMaxMonthlyRevenue() { return maxMonthlyRevenue; }

    public boolean isUnlimited(int value) {
        return value < 0;
    }

    public boolean hasFeature(String feature) {
        return switch (feature) {
            case "products", "customers", "sales", "inventory", "reports", "advanced_reports" -> true;
            case "multi_user", "api_access" -> this == PRO;
            default -> false;
        };
    }
}
