package com.osmar.boutiqueos.productcategory;

import jakarta.validation.constraints.NotBlank;

public record ProductCategoryRequest(
        @NotBlank String name,
        String description,
        String sizeLabel,
        Boolean active
) {
}
