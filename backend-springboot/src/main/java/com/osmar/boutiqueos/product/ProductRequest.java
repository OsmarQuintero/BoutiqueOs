package com.osmar.boutiqueos.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String category,
        @Size(max = 50) String size,
        @Size(max = 50) String color,
        @Size(max = 100) String sku,
        @Size(max = 10_000) String imageUrl,
        @DecimalMin("0.00") BigDecimal costPrice,
        @DecimalMin("0.00") BigDecimal salePrice,
        @Min(0) Integer stock,
        ProductStatus status
) {
}
