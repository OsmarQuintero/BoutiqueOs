package com.osmar.boutiqueos.report;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final DailyCashCountService dailyCashCountService;

    public ReportController(DailyCashCountService dailyCashCountService) {
        this.dailyCashCountService = dailyCashCountService;
    }

    @GetMapping("/cash-count/today")
    public DailyCashCountResponse todayCashCount(@RequestParam(required = false) LocalDate date) {
        return DailyCashCountResponse.from(dailyCashCountService.byDate(date));
    }

    @PutMapping("/cash-count/today")
    public DailyCashCountResponse saveTodayCashCount(@RequestParam(required = false) LocalDate date, @Valid @RequestBody DailyCashCountRequest request) {
        return DailyCashCountResponse.from(dailyCashCountService.saveByDate(date, request));
    }
}
