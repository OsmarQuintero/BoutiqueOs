package com.osmar.boutiqueos.promotion;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PromotionRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 40) String code,
        @NotNull PromotionType type,
        @NotNull @DecimalMin("0.01") BigDecimal value,
        @DecimalMin("0.00") BigDecimal minSubtotal,
        Long customerId,
        LocalDate startsAt,
        LocalDate endsAt,
        Boolean active,
        @Size(max = 1000) String notes
) {
}
