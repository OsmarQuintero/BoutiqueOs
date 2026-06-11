package com.osmar.boutiqueos.purchase;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseRequest(
        @NotNull Long productId,
        String supplierName,
        @Min(1) int quantity,
        @DecimalMin("0.00") BigDecimal unitCost,
        String note
) {
}
