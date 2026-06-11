package com.osmar.boutiqueos.report;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class DailyCashCountService {

    private final DailyCashCountRepository repository;

    public DailyCashCountService(DailyCashCountRepository repository) {
        this.repository = repository;
    }

    public DailyCashCount today() {
        return byDate(null);
    }

    public DailyCashCount byDate(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now(ZoneId.systemDefault()) : date;
        return repository.findByBusinessDate(target).orElseGet(() -> {
            DailyCashCount cashCount = new DailyCashCount();
            cashCount.setBusinessDate(target);
            cashCount.setActualCash(BigDecimal.ZERO);
            cashCount.setNotes(null);
            return repository.save(cashCount);
        });
    }

    public DailyCashCount saveToday(DailyCashCountRequest request) {
        return saveByDate(null, request);
    }

    public DailyCashCount saveByDate(LocalDate date, DailyCashCountRequest request) {
        DailyCashCount cashCount = byDate(date);
        cashCount.setActualCash(request.actualCash() == null ? BigDecimal.ZERO : request.actualCash());
        cashCount.setNotes(request.notes());
        cashCount.setUpdatedAt(Instant.now());
        return repository.save(cashCount);
    }
}
