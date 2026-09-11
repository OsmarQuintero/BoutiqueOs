package com.osmar.boutiqueos.subscription;

/**
 * @param plan     BASIC o PRO
 * @param interval "monthly" o "annual" (por defecto mensual)
 * @param priceId  ya no se usa: el precio lo decide el servidor. Se acepta para
 *                 no romper versiones viejas del sistema que lo mandan.
 */
public record CheckoutRequest(
        String plan,
        String interval,
        String priceId
) {}
