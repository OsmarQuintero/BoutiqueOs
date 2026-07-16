package com.osmar.boutiqueos.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyCashCountRepository extends JpaRepository<DailyCashCount, Long> {

    Optional<DailyCashCount> findByAccountIdAndBusinessDate(Long accountId, LocalDate businessDate);

    java.util.List<DailyCashCount> findAllByAccountIdOrderByBusinessDateDesc(Long accountId);
}
