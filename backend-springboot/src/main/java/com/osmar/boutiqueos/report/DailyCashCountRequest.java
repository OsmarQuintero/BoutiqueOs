package com.osmar.boutiqueos.report;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record DailyCashCountRequest(
        @DecimalMin("0.00") BigDecimal actualCash,
        String notes
) {
}
