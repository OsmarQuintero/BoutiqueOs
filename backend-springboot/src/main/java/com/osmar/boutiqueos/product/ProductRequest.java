package com.osmar.boutiqueos.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank String name,
        String category,
        String size,
        String color,
        String sku,
        String imageUrl,
        @DecimalMin("0.00") BigDecimal costPrice,
        @DecimalMin("0.00") BigDecimal salePrice,
        @Min(0) Integer stock,
        ProductStatus status
) {
}
