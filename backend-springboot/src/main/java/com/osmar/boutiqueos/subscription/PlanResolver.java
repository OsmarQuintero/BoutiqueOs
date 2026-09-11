package com.osmar.boutiqueos.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private final String priceBasic;
    private final String pricePro;

    public PlanResolver(
            @Value("${app.stripe.price-id:}") String legacyPriceId,
            @Value("${app.stripe.price-basic:}") String priceBasic,
            @Value("${app.stripe.price-pro:}") String pricePro
    ) {
        String resolvedBasic = (priceBasic != null && !priceBasic.isBlank())
                ? priceBasic
                : (legacyPriceId == null ? "" : legacyPriceId);
        this.priceBasic = resolvedBasic.trim();
        this.pricePro = pricePro == null ? "" : pricePro.trim();
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

        PlanType fromPrice = fromPriceId(priceId);
        if (fromPrice != null) {
            log.info("Plan deducido del precio {} porque el checkout no traia metadata.plan", priceId);
            return fromPrice;
        }

        log.warn(
                "No se pudo determinar el plan (metadata='{}', precio='{}'). Se asigna BASIC para no dejar al cliente sin acceso.",
                planFromMetadata, priceId
        );
        return PlanType.BASIC;
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

    private PlanType fromPriceId(String priceId) {
        if (priceId == null || priceId.isBlank()) {
            return null;
        }
        String trimmed = priceId.trim();
        if (!pricePro.isBlank() && pricePro.equals(trimmed)) {
            return PlanType.PRO;
        }
        if (!priceBasic.isBlank() && priceBasic.equals(trimmed)) {
            return PlanType.BASIC;
        }
        return null;
    }
}
