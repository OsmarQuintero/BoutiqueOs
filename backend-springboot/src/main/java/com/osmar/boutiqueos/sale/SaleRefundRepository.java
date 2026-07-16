package com.osmar.boutiqueos.sale;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SaleRefundRepository extends JpaRepository<SaleRefund, Long> {

    List<SaleRefund> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long accountId, Instant start, Instant end);

    List<SaleRefund> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);
}
