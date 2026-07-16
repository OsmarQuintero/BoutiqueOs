package com.osmar.boutiqueos.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement> findTop50ByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<InventoryMovement> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long accountId, Instant start, Instant end);
}
