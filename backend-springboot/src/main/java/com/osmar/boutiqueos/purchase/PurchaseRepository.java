package com.osmar.boutiqueos.purchase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findTop30ByOrderByCreatedAtDesc();

    List<Purchase> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant start, Instant end);
}
