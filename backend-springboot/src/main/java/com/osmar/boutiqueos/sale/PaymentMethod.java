package com.osmar.boutiqueos.sale;

public enum PaymentMethod {
    CASH,
    TRANSFER,
    CARD,
    /** Varios metodos en una venta; el desglose va en Sale.payments. */
    MIXED
}
