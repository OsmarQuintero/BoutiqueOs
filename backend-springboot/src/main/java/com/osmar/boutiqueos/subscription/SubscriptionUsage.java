package com.osmar.boutiqueos.subscription;

public record SubscriptionUsage(
        int productCount,
        int maxProducts,
        int customerCount,
        int maxCustomers,
        int salesThisMonth,
        int maxSalesPerMonth
) {}
