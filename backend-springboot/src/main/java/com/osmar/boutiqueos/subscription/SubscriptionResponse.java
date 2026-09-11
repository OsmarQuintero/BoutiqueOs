package com.osmar.boutiqueos.subscription;

import java.time.Instant;

/**
 * @param cancelAtPeriodEnd la suscripcion se cancelo pero sigue vigente hasta currentPeriodEnd
 * @param graceEndsAt       con pago vencido: hasta cuando se puede seguir vendiendo
 * @param accessBlocked     true = solo lectura (no se puede vender ni modificar)
 * @param canManageBilling  hay cliente en Stripe: se puede abrir el portal de pagos
 */
public record SubscriptionResponse(
        String plan,
        String planName,
        String status,
        String billingInterval,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant graceEndsAt,
        boolean accessBlocked,
        boolean canManageBilling,
        int maxStaffUsers,
        String stripeCustomerId,
        SubscriptionUsage usage
) {
    public static SubscriptionResponse from(AccountSubscription sub, SubscriptionUsage usage,
                                            Instant graceEndsAt, boolean accessBlocked) {
        PlanType planType = sub.getPlan();
        return new SubscriptionResponse(
                planType != null ? planType.name() : "NONE",
                planType != null ? planType.getDisplayName() : "Sin suscripción",
                sub.getStatus().name(),
                sub.getBillingInterval() == null ? BillingInterval.MONTHLY.name() : sub.getBillingInterval().name(),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                sub.isCancelAtPeriodEnd(),
                graceEndsAt,
                accessBlocked,
                sub.getStripeCustomerId() != null && !sub.getStripeCustomerId().isBlank(),
                planType == null ? 0 : planType.getMaxStaffUsers(),
                sub.getStripeCustomerId(),
                usage
        );
    }
}
