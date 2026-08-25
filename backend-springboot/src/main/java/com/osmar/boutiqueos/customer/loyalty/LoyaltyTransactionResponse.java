package com.osmar.boutiqueos.customer.loyalty;

import java.math.BigDecimal;
import java.time.Instant;

public record LoyaltyTransactionResponse(
        Long id,
        Long customerId,
        LoyaltyTransaction.TransactionType type,
        int points,
        Long saleId,
        Long rewardId,
        String description,
        Instant createdAt,
        Instant expiresAt
) {
    public static LoyaltyTransactionResponse from(LoyaltyTransaction t) {
        return new LoyaltyTransactionResponse(
                t.getId(),
                t.getCustomerId(),
                t.getType(),
                t.getPoints(),
                t.getSaleId(),
                t.getRewardId(),
                t.getDescription(),
                t.getCreatedAt(),
                t.getExpiresAt()
        );
    }
}
