package com.osmar.boutiqueos.layaway;

import com.osmar.boutiqueos.sale.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record LayawayResponse(
        Long id,
        Long customerId,
        String customerName,
        LayawayStatus status,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal remaining,
        BigDecimal refunded,
        LocalDate dueDate,
        boolean overdue,
        String notes,
        String createdByName,
        Instant createdAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancelReason,
        Long saleId,
        List<Item> items,
        List<Payment> payments
) {

    public static LayawayResponse from(Layaway layaway) {
        boolean overdue = layaway.getStatus() == LayawayStatus.OPEN
                && layaway.getDueDate() != null
                && layaway.getDueDate().isBefore(LocalDate.now());
        return new LayawayResponse(
                layaway.getId(),
                layaway.getCustomerId(),
                layaway.getCustomerName(),
                layaway.getStatus(),
                layaway.getTotal(),
                layaway.getPaid(),
                layaway.getRemaining(),
                layaway.getRefunded(),
                layaway.getDueDate(),
                overdue,
                layaway.getNotes(),
                layaway.getCreatedByName(),
                layaway.getCreatedAt(),
                layaway.getCompletedAt(),
                layaway.getCancelledAt(),
                layaway.getCancelReason(),
                layaway.getSaleId(),
                layaway.getItems().stream().map(Item::from).toList(),
                layaway.getPayments().stream()
                        .sorted(Comparator.comparing(LayawayPayment::getCreatedAt))
                        .map(Payment::from).toList()
        );
    }

    public record Item(Long productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        static Item from(LayawayItem item) {
            return new Item(item.getProductId(), item.getProductName(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal());
        }
    }

    public record Payment(Long id, PaymentMethod method, BigDecimal amount, Instant createdAt, String receivedByName, String note) {
        static Payment from(LayawayPayment payment) {
            return new Payment(payment.getId(), payment.getMethod(), payment.getAmount(), payment.getCreatedAt(),
                    payment.getReceivedByName(), payment.getNote());
        }
    }

    /** Abono de un dia, para el corte. */
    public record DayPayment(Long layawayId, String customerName, PaymentMethod method, BigDecimal amount, Instant createdAt, String note) {
        public static DayPayment from(LayawayPayment payment) {
            return new DayPayment(payment.getLayaway().getId(), payment.getLayaway().getCustomerName(), payment.getMethod(),
                    payment.getAmount(), payment.getCreatedAt(), payment.getNote());
        }
    }
}
