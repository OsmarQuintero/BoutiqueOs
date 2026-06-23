package com.osmar.boutiqueos.settings;

import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.inventory.InventoryMovementRepository;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.productcategory.ProductCategoryRepository;
import com.osmar.boutiqueos.purchase.PurchaseRepository;
import com.osmar.boutiqueos.report.DailyCashCountRepository;
import com.osmar.boutiqueos.sale.SaleRefundRepository;
import com.osmar.boutiqueos.sale.SaleRefundResponse;
import com.osmar.boutiqueos.sale.SaleRepository;
import com.osmar.boutiqueos.sale.SaleResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final AppSettingsService appSettingsService;
    private final AuthSessionService authSessionService;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final SaleRefundRepository saleRefundRepository;
    private final PurchaseRepository purchaseRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final DailyCashCountRepository dailyCashCountRepository;

    public BackupController(
            AppSettingsService appSettingsService,
            AuthSessionService authSessionService,
            ProductRepository productRepository,
            ProductCategoryRepository productCategoryRepository,
            CustomerRepository customerRepository,
            SaleRepository saleRepository,
            SaleRefundRepository saleRefundRepository,
            PurchaseRepository purchaseRepository,
            InventoryMovementRepository inventoryMovementRepository,
            DailyCashCountRepository dailyCashCountRepository
    ) {
        this.appSettingsService = appSettingsService;
        this.authSessionService = authSessionService;
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.saleRefundRepository = saleRefundRepository;
        this.purchaseRepository = purchaseRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.dailyCashCountRepository = dailyCashCountRepository;
    }

    @GetMapping
    public Map<String, Object> export(@RequestHeader(value = AuthSessionService.SESSION_HEADER, required = false) String token) {
        if (!authSessionService.isValid(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }

        Map<String, Object> backup = new LinkedHashMap<>();
        backup.put("generatedAt", Instant.now());
        backup.put("settings", AppSettingsResponse.from(appSettingsService.get()));
        backup.put("products", productRepository.findAll());
        backup.put("productCategories", productCategoryRepository.findAll());
        backup.put("customers", customerRepository.findAll());
        backup.put("sales", saleRepository.findAll().stream().map(SaleResponse::from).toList());
        backup.put("saleRefunds", saleRefundRepository.findAll().stream().map(SaleRefundResponse::from).toList());
        backup.put("purchases", purchaseRepository.findAll());
        backup.put("inventoryMovements", inventoryMovementRepository.findAll());
        backup.put("dailyCashCounts", dailyCashCountRepository.findAll());
        return backup;
    }
}
