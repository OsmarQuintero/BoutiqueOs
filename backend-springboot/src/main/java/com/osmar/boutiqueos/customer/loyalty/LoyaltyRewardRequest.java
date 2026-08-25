package com.osmar.boutiqueos.customer.loyalty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record LoyaltyRewardRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description,
        @Min(1) int pointsRequired,
        @NotNull LoyaltyReward.RewardType rewardType,
        @DecimalMin("0.00") BigDecimal discountAmount,
        Long productId,
        Boolean active
) {
}
