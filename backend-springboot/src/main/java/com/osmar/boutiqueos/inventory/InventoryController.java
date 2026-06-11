package com.osmar.boutiqueos.inventory;

import com.osmar.boutiqueos.product.Product;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/movements")
    public List<InventoryMovementResponse> movements(@RequestParam(required = false) LocalDate date) {
        return date == null ? inventoryService.listRecent() : inventoryService.listByDate(date);
    }

    @PostMapping("/adjustments")
    public Product adjust(@Valid @RequestBody InventoryAdjustmentRequest request) {
        return inventoryService.adjustStock(request);
    }
}
