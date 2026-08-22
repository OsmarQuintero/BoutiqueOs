package com.osmar.boutiqueos.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DailyCashCountResponse(
        Long id,
        LocalDate businessDate,
        BigDecimal openingFloat,
        BigDecimal actualCash,
        BigDecimal expectedCash,
        BigDecimal difference,
        String notes,
        boolean closed,
        Instant closedAt,
        String closedBy,
        String reopenedBy,
        String reopenedReason,
        Instant updatedAt
) {
    public static DailyCashCountResponse from(DailyCashCount count) {
        return new DailyCashCountResponse(
                count.getId(),
                count.getBusinessDate(),
                count.getOpeningFloat(),
                count.getActualCash(),
                count.getExpectedCash(),
                count.getDifference(),
                count.getNotes(),
                count.isClosed(),
                count.getClosedAt(),
                count.getClosedBy(),
                count.getReopenedBy(),
                count.getReopenedReason(),
                count.getUpdatedAt()
        );
    }
}
