package com.osmar.boutiqueos.report;

import com.osmar.boutiqueos.config.AccountContext;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class DailyCashCountService {

    private final DailyCashCountRepository repository;
    private final AccountContext accountContext;

    public DailyCashCountService(DailyCashCountRepository repository, AccountContext accountContext) {
        this.repository = repository;
        this.accountContext = accountContext;
    }

    public DailyCashCount today() {
        return byDate(null);
    }

    public DailyCashCount byDate(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now(ZoneId.systemDefault()) : date;
        Long accountId = accountContext.requireAccountId();
        return repository.findByAccountIdAndBusinessDate(accountId, target).orElseGet(() -> {
            DailyCashCount cashCount = new DailyCashCount();
            cashCount.setAccountId(accountId);
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
        if (cashCount.isClosed()) {
            throw new ResponseStatusException(CONFLICT, "El dia ya esta cerrado");
        }
        cashCount.setActualCash(request.actualCash() == null ? BigDecimal.ZERO : request.actualCash());
        cashCount.setNotes(request.notes());
        cashCount.setUpdatedAt(Instant.now());
        return repository.save(cashCount);
    }

    public DailyCashCount closeByDate(LocalDate date) {
        DailyCashCount cashCount = byDate(date);
        Instant now = Instant.now();
        cashCount.setClosed(true);
        cashCount.setClosedAt(now);
        cashCount.setUpdatedAt(now);
        return repository.save(cashCount);
    }

    public DailyCashCount reopenByDate(LocalDate date) {
        DailyCashCount cashCount = byDate(date);
        cashCount.setClosed(false);
        cashCount.setClosedAt(null);
        cashCount.setUpdatedAt(Instant.now());
        return repository.save(cashCount);
    }

    public List<DailyCashCount> history() {
        Long accountId = accountContext.requireAccountId();
        return repository.findAllByAccountIdOrderByBusinessDateDesc(accountId);
    }
}
