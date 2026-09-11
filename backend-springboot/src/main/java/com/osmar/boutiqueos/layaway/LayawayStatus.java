package com.osmar.boutiqueos.layaway;

public enum LayawayStatus {
    /** Apartado con saldo pendiente: la mercancia esta reservada. */
    OPEN,
    /** Liquidado: ya se registro como venta. */
    COMPLETED,
    /** Cancelado: la mercancia regreso al inventario. */
    CANCELLED
}
