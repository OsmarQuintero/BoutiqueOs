package com.osmar.boutiqueos.layaway;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LayawayRepository extends JpaRepository<Layaway, Long> {

    List<Layaway> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<Layaway> findAllByAccountIdAndStatusOrderByCreatedAtDesc(Long accountId, LayawayStatus status);

    Optional<Layaway> findByIdAndAccountId(Long id, Long accountId);

    void deleteAllByAccountId(Long accountId);

    /** Efectivo neto de abonos del periodo (las devoluciones de anticipo van en negativo). */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM LayawayPayment p " +
           "WHERE p.layaway.accountId = :accountId AND p.method = 'CASH' " +
           "AND p.createdAt >= :start AND p.createdAt < :end")
    BigDecimal sumCashPayments(@Param("accountId") Long accountId,
                               @Param("start") Instant start,
                               @Param("end") Instant end);

    @Query("SELECT p FROM LayawayPayment p JOIN FETCH p.layaway l " +
           "WHERE l.accountId = :accountId AND p.createdAt >= :start AND p.createdAt < :end " +
           "ORDER BY p.createdAt ASC")
    List<LayawayPayment> findPayments(@Param("accountId") Long accountId,
                                      @Param("start") Instant start,
                                      @Param("end") Instant end);
}
