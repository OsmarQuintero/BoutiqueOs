package com.osmar.boutiqueos.sale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record SaleRequest(
        @NotNull PaymentMethod paymentMethod,
        @DecimalMin("0.00") BigDecimal discount,
        @DecimalMin("0.00") BigDecimal cashReceived,
        Long customerId,
        @NotEmpty List<@Valid SaleItemRequest> items
) {
    public record SaleItemRequest(
            @NotNull Long productId,
            @Min(1) int quantity
    ) {
    }
}
