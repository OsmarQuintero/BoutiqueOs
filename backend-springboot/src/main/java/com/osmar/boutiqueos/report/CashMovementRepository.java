package com.osmar.boutiqueos.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    List<CashMovement> findByAccountIdAndBusinessDateOrderByCreatedAtDesc(Long accountId, LocalDate businessDate);

    List<CashMovement> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);

    @Query("SELECT COALESCE(SUM(CASE WHEN cm.type IN ('CASH_IN', 'TIP') THEN cm.amount ELSE 0 END), 0) " +
           "- COALESCE(SUM(CASE WHEN cm.type IN ('CASH_OUT', 'DEPOSIT') THEN cm.amount ELSE 0 END), 0) " +
           "FROM CashMovement cm WHERE cm.accountId = :accountId AND cm.businessDate = :businessDate")
    BigDecimal sumNetMovements(@Param("accountId") Long accountId, @Param("businessDate") LocalDate businessDate);
}
