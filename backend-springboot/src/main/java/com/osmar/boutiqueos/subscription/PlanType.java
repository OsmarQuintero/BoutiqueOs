package com.osmar.boutiqueos.subscription;

public enum PlanType {
    FREE("Boutique OS Free", 50, 100, 200, 5),
    BASIC("Boutique OS Básico", 500, 1000, 5000, 50),
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
            case "products", "customers", "sales", "inventory" -> true;
            case "reports", "multi_user", "api_access" -> this == PRO;
            case "advanced_reports" -> this == BASIC || this == PRO;
            default -> false;
        };
    }
}
