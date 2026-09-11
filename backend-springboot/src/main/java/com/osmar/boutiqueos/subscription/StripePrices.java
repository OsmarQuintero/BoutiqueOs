package com.osmar.boutiqueos.subscription;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Los cuatro precios de Stripe (plan x periodo) en un solo lugar.
 *
 * <p>El servidor siempre decide el precio a partir del plan y el periodo: antes
 * el navegador podia mandar el priceId y un plan distinto en la metadata, o sea,
 * pagar Basico y quedar como Pro.
 */
@Component
public class StripePrices {

    public record Match(PlanType plan, BillingInterval interval) {
    }

    private final String basicMonthly;
    private final String basicAnnual;
    private final String proMonthly;
    private final String proAnnual;

    public StripePrices(
            @Value("${app.stripe.price-id:}") String legacyPriceId,
            @Value("${app.stripe.price-basic:}") String basicMonthly,
            @Value("${app.stripe.price-basic-annual:}") String basicAnnual,
            @Value("${app.stripe.price-pro:}") String proMonthly,
            @Value("${app.stripe.price-pro-annual:}") String proAnnual
    ) {
        this.basicMonthly = clean(blank(basicMonthly) ? legacyPriceId : basicMonthly);
        this.basicAnnual = clean(basicAnnual);
        this.proMonthly = clean(proMonthly);
        this.proAnnual = clean(proAnnual);
    }

    /** "" si ese precio no esta configurado. */
    public String priceFor(PlanType plan, BillingInterval interval) {
        return switch (plan) {
            case BASIC -> interval == BillingInterval.ANNUAL ? basicAnnual : basicMonthly;
            case PRO -> interval == BillingInterval.ANNUAL ? proAnnual : proMonthly;
        };
    }

    public Optional<Match> resolve(String priceId) {
        String id = clean(priceId);
        if (id.isEmpty()) return Optional.empty();
        if (id.equals(basicMonthly)) return Optional.of(new Match(PlanType.BASIC, BillingInterval.MONTHLY));
        if (id.equals(basicAnnual)) return Optional.of(new Match(PlanType.BASIC, BillingInterval.ANNUAL));
        if (id.equals(proMonthly)) return Optional.of(new Match(PlanType.PRO, BillingInterval.MONTHLY));
        if (id.equals(proAnnual)) return Optional.of(new Match(PlanType.PRO, BillingInterval.ANNUAL));
        return Optional.empty();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
