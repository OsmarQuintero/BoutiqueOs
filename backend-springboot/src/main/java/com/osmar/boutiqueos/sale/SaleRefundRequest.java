package com.osmar.boutiqueos.sale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaleRefundRequest(
        @NotEmpty List<@Valid Item> items
) {
    public record Item(
            @NotNull Long saleItemId,
            @Min(1) int quantity
    ) {
    }
}
