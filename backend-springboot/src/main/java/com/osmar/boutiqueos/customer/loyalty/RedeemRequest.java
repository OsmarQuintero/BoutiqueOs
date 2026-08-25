package com.osmar.boutiqueos.customer.loyalty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RedeemRequest(
        @NotNull Long rewardId,
        @Min(1) int quantity
) {
}
