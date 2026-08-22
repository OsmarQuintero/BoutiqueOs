package com.osmar.boutiqueos.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CashMovementResponse(
        Long id,
        LocalDate businessDate,
        CashMovement.MovementType type,
        BigDecimal amount,
        String description,
        Instant createdAt
) {
    public static CashMovementResponse from(CashMovement movement) {
        return new CashMovementResponse(
                movement.getId(),
                movement.getBusinessDate(),
                movement.getType(),
                movement.getAmount(),
                movement.getDescription(),
                movement.getCreatedAt()
        );
    }
}
