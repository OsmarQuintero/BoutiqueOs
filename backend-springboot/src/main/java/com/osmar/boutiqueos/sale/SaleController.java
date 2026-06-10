package com.osmar.boutiqueos.sale;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping("/today")
    public List<SaleResponse> today() {
        return saleService.listToday().stream().map(SaleResponse::from).toList();
    }

    @GetMapping("/pending")
    public List<SaleResponse> pending() {
        return saleService.listPending().stream().map(SaleResponse::from).toList();
    }

    @PostMapping("/{id}/confirm")
    public SaleResponse confirm(@PathVariable Long id) {
        return SaleResponse.from(saleService.confirm(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse create(@Valid @RequestBody SaleRequest request) {
        return SaleResponse.from(saleService.create(request));
    }
}
