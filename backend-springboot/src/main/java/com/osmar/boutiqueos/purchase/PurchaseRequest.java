package com.osmar.boutiqueos.purchase;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PurchaseRequest(
        @NotNull Long productId,
        @Size(max = 200) String supplierName,
        @Min(1) int quantity,
        @DecimalMin("0.00") BigDecimal unitCost,
        @Size(max = 500) String note
) {
}
