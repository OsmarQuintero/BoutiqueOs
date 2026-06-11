package com.osmar.boutiqueos.sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleRefundResponse(
        Long id,
        Long saleId,
        PaymentMethod paymentMethod,
        String customerName,
        BigDecimal total,
        BigDecimal estimatedProfit,
        Instant createdAt,
        List<Item> items
) {
    public static SaleRefundResponse from(SaleRefund refund) {
        return new SaleRefundResponse(
                refund.getId(),
                refund.getSaleId(),
                refund.getPaymentMethod(),
                refund.getCustomerName(),
                refund.getTotal(),
                refund.getEstimatedProfit(),
                refund.getCreatedAt(),
                refund.getItems().stream().map(Item::from).toList()
        );
    }

    public record Item(
            Long id,
            Long saleItemId,
            Long productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal total,
            BigDecimal estimatedProfit
    ) {
        public static Item from(SaleRefundItem item) {
            return new Item(
                    item.getId(),
                    item.getSaleItemId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getTotal(),
                    item.getEstimatedProfit()
            );
        }
    }
}
