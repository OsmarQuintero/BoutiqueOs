package com.osmar.boutiqueos.report;

import com.osmar.boutiqueos.config.AccountContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class CashMovementService {

    private final CashMovementRepository repository;
    private final AccountContext accountContext;

    public CashMovementService(CashMovementRepository repository, AccountContext accountContext) {
        this.repository = repository;
        this.accountContext = accountContext;
    }

    @Transactional
    public CashMovement create(LocalDate date, CashMovementRequest request) {
        Long accountId = accountContext.requireAccountId();
        LocalDate target = date == null ? LocalDate.now(ZoneId.systemDefault()) : date;

        CashMovement movement = new CashMovement();
        movement.setAccountId(accountId);
        movement.setBusinessDate(target);
        movement.setType(request.type());
        movement.setAmount(request.amount());
        movement.setDescription(request.description().trim());
        movement.setCreatedAt(Instant.now());
        return repository.save(movement);
    }

    @Transactional(readOnly = true)
    public List<CashMovement> listByDate(LocalDate date) {
        Long accountId = accountContext.requireAccountId();
        LocalDate target = date == null ? LocalDate.now(ZoneId.systemDefault()) : date;
        return repository.findByAccountIdAndBusinessDateOrderByCreatedAtDesc(accountId, target);
    }

    @Transactional
    public void delete(Long movementId) {
        Long accountId = accountContext.requireAccountId();
        CashMovement movement = repository.findById(movementId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Movement not found"));
        if (!movement.getAccountId().equals(accountId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Movement not found");
        }
        repository.delete(movement);
    }
}
