package com.osmar.boutiqueos.promotion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);
    Optional<Promotion> findByIdAndAccountId(Long id, Long accountId);
    Optional<Promotion> findByAccountIdAndCodeIgnoreCase(Long accountId, String code);
    void deleteAllByAccountId(Long accountId);
}
