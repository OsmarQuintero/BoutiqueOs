package com.osmar.boutiqueos.purchase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findTop30ByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<Purchase> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long accountId, Instant start, Instant end);
}
