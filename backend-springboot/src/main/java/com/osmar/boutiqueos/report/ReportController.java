package com.osmar.boutiqueos.report;

import com.osmar.boutiqueos.subscription.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final DailyCashCountService dailyCashCountService;
    private final CashMovementService cashMovementService;
    private final SubscriptionService subscriptionService;

    public ReportController(DailyCashCountService dailyCashCountService, CashMovementService cashMovementService, SubscriptionService subscriptionService) {
        this.dailyCashCountService = dailyCashCountService;
        this.cashMovementService = cashMovementService;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/cash-count/today")
    public DailyCashCountResponse todayCashCount(@RequestParam(required = false) LocalDate date) {
        subscriptionService.requireFeature("cash_count");
        return DailyCashCountResponse.from(dailyCashCountService.byDate(date));
    }

    @GetMapping("/cash-count/history")
    public List<DailyCashCountResponse> cashCountHistory() {
        subscriptionService.requireFeature("cash_count");
        return dailyCashCountService.history().stream().map(DailyCashCountResponse::from).toList();
    }

    @PutMapping("/cash-count/today")
    public DailyCashCountResponse saveTodayCashCount(@RequestParam(required = false) LocalDate date, @Valid @RequestBody DailyCashCountRequest request) {
        subscriptionService.requireFeature("cash_count");
        return DailyCashCountResponse.from(dailyCashCountService.saveByDate(date, request));
    }

    @PostMapping("/cash-count/today/close")
    public DailyCashCountResponse closeTodayCashCount(@RequestParam(required = false) LocalDate date) {
        subscriptionService.requireFeature("cash_count");
        return DailyCashCountResponse.from(dailyCashCountService.closeByDate(date));
    }

    @PostMapping("/cash-count/today/reopen")
    public DailyCashCountResponse reopenTodayCashCount(
            @RequestParam(required = false) LocalDate date,
            @RequestBody(required = false) ReopenRequest request
    ) {
        subscriptionService.requireFeature("cash_count");
        String reason = request != null ? request.reason() : null;
        return DailyCashCountResponse.from(dailyCashCountService.reopenByDate(date, reason, "admin"));
    }

    @PostMapping("/cash-movements")
    public CashMovementResponse createMovement(@RequestParam(required = false) LocalDate date, @Valid @RequestBody CashMovementRequest request) {
        subscriptionService.requireFeature("cash_count");
        return CashMovementResponse.from(cashMovementService.create(date, request));
    }

    @GetMapping("/cash-movements")
    public List<CashMovementResponse> listMovements(@RequestParam(required = false) LocalDate date) {
        subscriptionService.requireFeature("cash_count");
        return cashMovementService.listByDate(date).stream().map(CashMovementResponse::from).toList();
    }

    @DeleteMapping("/cash-movements/{id}")
    public void deleteMovement(@PathVariable Long id) {
        subscriptionService.requireFeature("cash_count");
        cashMovementService.delete(id);
    }

    public record ReopenRequest(String reason) {}
}
