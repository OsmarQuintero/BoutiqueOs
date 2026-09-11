package com.osmar.boutiqueos.layaway;

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

/** Apartados. Cancelar es solo de la duena (lo cuida CashierPolicy). */
@RestController
@RequestMapping("/api/layaways")
public class LayawayController {

    private final LayawayService layawayService;

    public LayawayController(LayawayService layawayService) {
        this.layawayService = layawayService;
    }

    @GetMapping
    public List<LayawayResponse> list(@RequestParam(required = false) LayawayStatus status) {
        return layawayService.list(status).stream().map(LayawayResponse::from).toList();
    }

    @GetMapping("/payments")
    public List<LayawayResponse.DayPayment> payments(@RequestParam(required = false) LocalDate date) {
        return layawayService.paymentsOn(date).stream().map(LayawayResponse.DayPayment::from).toList();
    }

    @GetMapping("/{id}")
    public LayawayResponse get(@PathVariable Long id) {
        return LayawayResponse.from(layawayService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LayawayResponse create(@Valid @RequestBody LayawayRequests.Create request) {
        return LayawayResponse.from(layawayService.create(request));
    }

    @PostMapping("/{id}/payments")
    public LayawayResponse pay(@PathVariable Long id, @Valid @RequestBody LayawayRequests.Payment request) {
        return LayawayResponse.from(layawayService.pay(id, request));
    }

    @PostMapping("/{id}/cancel")
    public LayawayResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) LayawayRequests.Cancel request) {
        return LayawayResponse.from(layawayService.cancel(id, request));
    }
}
