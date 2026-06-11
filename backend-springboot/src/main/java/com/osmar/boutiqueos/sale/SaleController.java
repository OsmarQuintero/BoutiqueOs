package com.osmar.boutiqueos.sale;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping("/today")
    public List<SaleResponse> today(@RequestParam(required = false) LocalDate date) {
        return saleService.listByDate(date).stream().map(SaleResponse::from).toList();
    }

    @GetMapping
    public List<SaleResponse> all(@RequestParam(required = false) LocalDate date) {
        return (date == null ? saleService.listAll() : saleService.listByDate(date)).stream().map(SaleResponse::from).toList();
    }

    @GetMapping("/refunds/today")
    public List<SaleRefundResponse> refundsToday(@RequestParam(required = false) LocalDate date) {
        return saleService.listRefundsByDate(date).stream().map(SaleRefundResponse::from).toList();
    }

    @GetMapping("/pending")
    public List<SaleResponse> pending() {
        return saleService.listPending().stream().map(SaleResponse::from).toList();
    }

    @GetMapping("/customer/{customerId}")
    public List<SaleResponse> byCustomer(@PathVariable Long customerId) {
        return saleService.listByCustomer(customerId).stream().map(SaleResponse::from).toList();
    }

    @PostMapping("/{id}/confirm")
    public SaleResponse confirm(@PathVariable Long id) {
        return SaleResponse.from(saleService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    public SaleResponse cancel(@PathVariable Long id) {
        return SaleResponse.from(saleService.cancel(id));
    }

    @PostMapping("/{id}/refund")
    public SaleResponse refund(@PathVariable Long id, @Valid @RequestBody(required = false) SaleRefundRequest request) {
        return SaleResponse.from(saleService.refund(id, request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse create(@Valid @RequestBody SaleRequest request) {
        return SaleResponse.from(saleService.create(request));
    }
}
