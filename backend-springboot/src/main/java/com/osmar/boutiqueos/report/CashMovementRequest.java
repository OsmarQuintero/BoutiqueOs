package com.osmar.boutiqueos.report;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CashMovementRequest(
        @NotNull CashMovement.MovementType type,
        @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(max = 500) String description
) {
}
