package com.osmar.boutiqueos.sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleResponse(
        Long id,
        PaymentMethod paymentMethod,
        SaleStatus status,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        BigDecimal estimatedProfit,
        Long customerId,
        String customerName,
        Instant createdAt,
        List<Item> items
) {
    public static SaleResponse from(Sale sale) {
        return new SaleResponse(
                sale.getId(),
                sale.getPaymentMethod(),
                sale.getStatus(),
                sale.getSubtotal(),
                sale.getDiscount(),
                sale.getTotal(),
                sale.getEstimatedProfit(),
                sale.getCustomerId(),
                sale.getCustomerName(),
                sale.getCreatedAt(),
                sale.getItems().stream().map(Item::from).toList()
        );
    }

    public record Item(
            Long id,
            Long productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
        public static Item from(SaleItem item) {
            return new Item(
                    item.getId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getLineTotal()
            );
        }
    }
}
