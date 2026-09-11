package com.osmar.boutiqueos.sale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que manda el punto de venta al cobrar.
 *
 * <p>El descuento ya no se confia: el POS manda que promocion eligio
 * ({@code promotionId}) y cuanto descuento manual dio ({@code manualDiscount}),
 * y el servidor recalcula el total. {@code discount} queda solo para las ventas
 * que ya estaban en la cola sin conexion con el formato viejo: se trata como
 * descuento manual, con los mismos limites.
 */
public record SaleRequest(
        @NotNull PaymentMethod paymentMethod,
        @DecimalMin("0.00") BigDecimal discount,
        @DecimalMin("0.00") BigDecimal cashReceived,
        Long customerId,
        @NotEmpty List<@Valid SaleItemRequest> items,
        Long promotionId,
        @DecimalMin("0.00") BigDecimal manualDiscount,
        List<@Valid PaymentPart> payments
) {

    /** Sin desglose de pago (todo con un solo metodo). */
    public SaleRequest(
            PaymentMethod paymentMethod,
            BigDecimal discount,
            BigDecimal cashReceived,
            Long customerId,
            List<SaleItemRequest> items,
            Long promotionId,
            BigDecimal manualDiscount
    ) {
        this(paymentMethod, discount, cashReceived, customerId, items, promotionId, manualDiscount, null);
    }

    /** Formato anterior (sin promocion ni descuento manual separados). */
    public SaleRequest(
            PaymentMethod paymentMethod,
            BigDecimal discount,
            BigDecimal cashReceived,
            Long customerId,
            List<SaleItemRequest> items
    ) {
        this(paymentMethod, discount, cashReceived, customerId, items, null, null);
    }

    /** Pago mixto: cuanto se cobro con cada metodo. */
    public record PaymentPart(
            @NotNull PaymentMethod method,
            @NotNull @DecimalMin("0.00") BigDecimal amount
    ) {
    }

    public record SaleItemRequest(
            @NotNull Long productId,
            @Min(1) int quantity
    ) {
    }
}
