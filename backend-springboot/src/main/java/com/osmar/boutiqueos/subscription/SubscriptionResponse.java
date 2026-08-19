package com.osmar.boutiqueos.subscription;

import java.time.Instant;

public record SubscriptionResponse(
        String plan,
        String planName,
        String status,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        String stripeCustomerId,
        SubscriptionUsage usage
) {
    public static SubscriptionResponse from(AccountSubscription sub, SubscriptionUsage usage) {
        PlanType planType = sub.getPlan();
        return new SubscriptionResponse(
                planType != null ? planType.name() : "NONE",
                planType != null ? planType.getDisplayName() : "Sin suscripción",
                sub.getStatus().name(),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                sub.getStripeCustomerId(),
                usage
        );
    }
}
