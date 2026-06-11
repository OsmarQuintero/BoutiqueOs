package com.osmar.boutiqueos.inventory;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryMovementResponse(
        Long id,
        Long productId,
        String productName,
        InventoryMovementType type,
        int quantity,
        BigDecimal unitCost,
        String note,
        Instant createdAt
) {
    public static InventoryMovementResponse from(InventoryMovement movement) {
        return new InventoryMovementResponse(
                movement.getId(),
                movement.getProductId(),
                movement.getProductName(),
                movement.getType(),
                movement.getQuantity(),
                movement.getUnitCost(),
                movement.getNote(),
                movement.getCreatedAt()
        );
    }
}
