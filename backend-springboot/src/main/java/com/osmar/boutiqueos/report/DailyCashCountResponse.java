package com.osmar.boutiqueos.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DailyCashCountResponse(
        LocalDate businessDate,
        BigDecimal actualCash,
        String notes,
        Instant updatedAt
) {
    public static DailyCashCountResponse from(DailyCashCount count) {
        return new DailyCashCountResponse(
                count.getBusinessDate(),
                count.getActualCash(),
                count.getNotes(),
                count.getUpdatedAt()
        );
    }
}
