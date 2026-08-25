package com.osmar.boutiqueos.customer.loyalty;

import java.math.BigDecimal;

public record LoyaltyRewardResponse(
        Long id,
        String name,
        String description,
        int pointsRequired,
        LoyaltyReward.RewardType rewardType,
        BigDecimal discountAmount,
        Long productId,
        boolean active
) {
    public static LoyaltyRewardResponse from(LoyaltyReward r) {
        return new LoyaltyRewardResponse(
                r.getId(),
                r.getName(),
                r.getDescription(),
                r.getPointsRequired(),
                r.getRewardType(),
                r.getDiscountAmount(),
                r.getProductId(),
                r.isActive()
        );
    }
}
