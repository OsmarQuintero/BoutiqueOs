package com.osmar.boutiqueos.purchase;

import java.math.BigDecimal;
import java.time.Instant;

public record PurchaseResponse(
        Long id,
        String supplierName,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitCost,
        BigDecimal totalCost,
        String note,
        Instant createdAt
) {
    public static PurchaseResponse from(Purchase purchase) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getSupplierName(),
                purchase.getProductId(),
                purchase.getProductName(),
                purchase.getQuantity(),
                purchase.getUnitCost(),
                purchase.getTotalCost(),
                purchase.getNote(),
                purchase.getCreatedAt()
        );
    }
}
