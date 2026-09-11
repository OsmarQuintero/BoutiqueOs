package com.osmar.boutiqueos.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decide que plan le toca a una cuenta despues de un pago confirmado.
 *
 * <p>Existe porque dejar el plan en null bloquea al cliente: {@code checkLimits}
 * y {@code requireFeature} responden "No tienes una suscripcion activa" cuando
 * {@code plan == null}, aunque Stripe le siga cobrando cada mes. Cuando el pago
 * ya esta confirmado, nunca hay que devolver null: si no se puede determinar el
 * plan se cae a BASIC, que es lo mas conservador y deja al cliente operando.
 */
@Component
public class PlanResolver {

    private static final Logger log = LoggerFactory.getLogger(PlanResolver.class);

    private final StripePrices stripePrices;

    public PlanResolver(StripePrices stripePrices) {
        this.stripePrices = stripePrices;
    }

    /**
     * @param planFromMetadata valor de {@code metadata.plan} del checkout (puede venir vacio
     *                         en cobros viejos o creados fuera de la app)
     * @param priceId          precio real cobrado, como respaldo cuando no hay metadata
     */
    public PlanType resolve(String planFromMetadata, String priceId) {
        PlanType fromMetadata = fromName(planFromMetadata);
        if (fromMetadata != null) {
            return fromMetadata;
        }

        var fromPrice = stripePrices.resolve(priceId);
        if (fromPrice.isPresent()) {
            log.info("Plan deducido del precio {} porque el checkout no traia metadata.plan", priceId);
            return fromPrice.get().plan();
        }

        log.warn(
                "No se pudo determinar el plan (metadata='{}', precio='{}'). Se asigna BASIC para no dejar al cliente sin acceso.",
                planFromMetadata, priceId
        );
        return PlanType.BASIC;
    }

    /** Periodo del precio cobrado; si no se reconoce, mensual. */
    public BillingInterval resolveInterval(String intervalFromMetadata, String priceId) {
        var fromPrice = stripePrices.resolve(priceId);
        if (fromPrice.isPresent()) {
            return fromPrice.get().interval();
        }
        return BillingInterval.parse(intervalFromMetadata);
    }

    private PlanType fromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return PlanType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
