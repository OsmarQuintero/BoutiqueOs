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

    // Entra a la caja el total (lo recibido menos el cambio). Antes restaba el
    // cambio al total y una venta de $500 pagada con $1,000 contaba $0. Tambien
    // cuentan las ventas con devolucion: la devolucion se resta aparte.
    List<Sale> findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(Long accountId, Instant start, Instant end);

    @Query("SELECT COALESCE(SUM(s.total), 0) FROM Sale s " +
           "WHERE s.accountId = :accountId AND s.status IN ('CONFIRMED', 'PARTIALLY_REFUNDED', 'REFUNDED') " +
           "AND s.paymentMethod = 'CASH' AND s.layawayId IS NULL AND s.createdAt >= :start AND s.createdAt < :end")
    BigDecimal sumCashSalesTotal(@Param("accountId") Long accountId,
                                  @Param("start") Instant start,
                                  @Param("end") Instant end);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Sale s JOIN s.payments p " +
           "WHERE s.accountId = :accountId AND s.status IN ('CONFIRMED', 'PARTIALLY_REFUNDED', 'REFUNDED') " +
           "AND s.paymentMethod = 'MIXED' AND p.method = 'CASH' AND s.layawayId IS NULL " +
           "AND s.createdAt >= :start AND s.createdAt < :end")
    BigDecimal sumCashPartOfMixedSales(@Param("accountId") Long accountId,
                                       @Param("start") Instant start,
                                       @Param("end") Instant end);

    void deleteAllByAccountId(Long accountId);
}
