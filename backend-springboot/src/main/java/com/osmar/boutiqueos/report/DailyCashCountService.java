package com.osmar.boutiqueos.report;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.sale.SaleRefundRepository;
import com.osmar.boutiqueos.sale.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class DailyCashCountService {

    private final DailyCashCountRepository repository;
    private final CashMovementRepository cashMovementRepository;
    private final SaleRepository saleRepository;
    private final SaleRefundRepository saleRefundRepository;
    private final AccountContext accountContext;

    public DailyCashCountService(
            DailyCashCountRepository repository,
            CashMovementRepository cashMovementRepository,
            SaleRepository saleRepository,
            SaleRefundRepository saleRefundRepository,
            AccountContext accountContext
    ) {
        this.repository = repository;
        this.cashMovementRepository = cashMovementRepository;
        this.saleRepository = saleRepository;
        this.saleRefundRepository = saleRefundRepository;
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
            cashCount.setOpeningFloat(BigDecimal.ZERO);
            cashCount.setActualCash(BigDecimal.ZERO);
            cashCount.setExpectedCash(BigDecimal.ZERO);
            cashCount.setDifference(BigDecimal.ZERO);
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
            throw new ResponseStatusException(CONFLICT, "El día ya está cerrado. Reabre el día para modificar.");
        }

        cashCount.setOpeningFloat(request.openingFloat() == null ? BigDecimal.ZERO : request.openingFloat());
        cashCount.setActualCash(request.actualCash() == null ? BigDecimal.ZERO : request.actualCash());
        cashCount.setNotes(request.notes());

        BigDecimal expected = calculateExpectedCash(cashCount.getAccountId(), cashCount.getBusinessDate(), cashCount.getOpeningFloat());
        cashCount.setExpectedCash(expected);
        cashCount.setDifference(cashCount.getActualCash().subtract(expected));
        cashCount.setUpdatedAt(Instant.now());
        return repository.save(cashCount);
    }

    public DailyCashCount closeByDate(LocalDate date) {
        Long accountId = accountContext.requireAccountId();
        LocalDate target = date == null ? LocalDate.now(ZoneId.systemDefault()) : date;
        DailyCashCount cashCount = repository.findByAccountIdAndBusinessDate(accountId, target)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "No hay registro de corte para este día"));

        if (cashCount.isClosed()) {
            throw new ResponseStatusException(CONFLICT, "El día ya está cerrado");
        }
        if (cashCount.getActualCash().compareTo(BigDecimal.ZERO) == 0 && cashCount.getOpeningFloat().compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Debes guardar el efectivo real antes de cerrar el día");
        }

        BigDecimal expected = calculateExpectedCash(accountId, target, cashCount.getOpeningFloat());
        cashCount.setExpectedCash(expected);
        cashCount.setDifference(cashCount.getActualCash().subtract(expected));

        Instant now = Instant.now();
        cashCount.setClosed(true);
        cashCount.setClosedAt(now);
        cashCount.setUpdatedAt(now);
        return repository.save(cashCount);
    }

    public DailyCashCount reopenByDate(LocalDate date, String reason, String reopenedBy) {
        Long accountId = accountContext.requireAccountId();
        LocalDate target = date == null ? LocalDate.now(ZoneId.systemDefault()) : date;
        DailyCashCount cashCount = repository.findByAccountIdAndBusinessDate(accountId, target)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "No hay registro de corte para este día"));

        if (!cashCount.isClosed()) {
            throw new ResponseStatusException(CONFLICT, "El día no está cerrado");
        }
        if (reason == null || reason.trim().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Debes indicar el motivo para reabrir el día");
        }

        cashCount.setClosed(false);
        cashCount.setClosedAt(null);
        cashCount.setReopenedBy(reopenedBy);
        cashCount.setReopenedReason(reason.trim());
        cashCount.setUpdatedAt(Instant.now());
        return repository.save(cashCount);
    }

    public List<DailyCashCount> history() {
        Long accountId = accountContext.requireAccountId();
        return repository.findAllByAccountIdOrderByBusinessDateDesc(accountId);
    }

    private BigDecimal calculateExpectedCash(Long accountId, LocalDate date, BigDecimal openingFloat) {
        Instant startOfDay = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal cashSales = saleRepository.sumCashSalesTotal(accountId, startOfDay, endOfDay);
        BigDecimal cashRefunds = saleRefundRepository.sumCashRefundsTotal(accountId, startOfDay, endOfDay);
        BigDecimal netMovements = cashMovementRepository.sumNetMovements(accountId, date);

        return openingFloat.add(cashSales).subtract(cashRefunds).add(netMovements);
    }
}
