package com.osmar.boutiqueos.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long accountId, Instant start, Instant end);

    List<Sale> findByAccountIdAndCustomerIdOrderByCreatedAtDesc(Long accountId, Long customerId);

    List<Sale> findByAccountIdAndStatus(Long accountId, SaleStatus status);

    List<Sale> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);

    java.util.Optional<Sale> findByIdAndAccountId(Long id, Long accountId);

    long countByAccountId(Long accountId);
    long countByAccountIdAndCreatedAtAfter(Long accountId, Instant after);

    @Query("SELECT COALESCE(SUM(s.total - s.changeDue), 0) FROM Sale s " +
           "WHERE s.accountId = :accountId AND s.status = 'CONFIRMED' " +
           "AND s.paymentMethod = 'CASH' AND s.createdAt >= :start AND s.createdAt < :end")
    BigDecimal sumCashSalesTotal(@Param("accountId") Long accountId,
                                  @Param("start") Instant start,
                                  @Param("end") Instant end);
}
