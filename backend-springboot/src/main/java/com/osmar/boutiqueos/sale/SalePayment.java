package com.osmar.boutiqueos.sale;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

/** Una parte de un pago mixto: cuanto se pago con cada metodo. */
@Embeddable
public class SalePayment {

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    public SalePayment() {
    }

    public SalePayment(PaymentMethod method, BigDecimal amount) {
        this.method = method;
        this.amount = amount;
    }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
