package com.osmar.boutiqueos.sale;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant start, Instant end);

    List<Sale> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Sale> findByStatus(SaleStatus status);
}
