package com.osmar.boutiqueos.inventory;

import jakarta.validation.constraints.NotNull;

public record InventoryAdjustmentRequest(
        @NotNull Long productId,
        int quantityDelta,
        String note
) {
}
