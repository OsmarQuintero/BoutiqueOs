package com.osmar.boutiqueos.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface SaleRefundRepository extends JpaRepository<SaleRefund, Long> {

    List<SaleRefund> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long accountId, Instant start, Instant end);

    List<SaleRefund> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);

    @Query("SELECT COALESCE(SUM(r.total), 0) FROM SaleRefund r " +
           "WHERE r.accountId = :accountId AND r.paymentMethod = 'CASH' " +
           "AND r.createdAt >= :start AND r.createdAt < :end")
    BigDecimal sumCashRefundsTotal(@Param("accountId") Long accountId,
                                    @Param("start") Instant start,
                                    @Param("end") Instant end);
}
