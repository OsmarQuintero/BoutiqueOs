package com.osmar.boutiqueos.sale;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long accountId, Instant start, Instant end);

    List<Sale> findByAccountIdAndCustomerIdOrderByCreatedAtDesc(Long accountId, Long customerId);

    List<Sale> findByAccountIdAndStatus(Long accountId, SaleStatus status);

    List<Sale> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);

    java.util.Optional<Sale> findByIdAndAccountId(Long id, Long accountId);
}
