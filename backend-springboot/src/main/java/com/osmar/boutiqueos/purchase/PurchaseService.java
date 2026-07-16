package com.osmar.boutiqueos.purchase;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.inventory.InventoryMovementType;
import com.osmar.boutiqueos.inventory.InventoryService;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final AccountContext accountContext;

    public PurchaseService(PurchaseRepository purchaseRepository, ProductRepository productRepository, InventoryService inventoryService, AccountContext accountContext) {
        this.purchaseRepository = purchaseRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.accountContext = accountContext;
    }

    public List<PurchaseResponse> listRecent() {
        return purchaseRepository.findTop30ByAccountIdOrderByCreatedAtDesc(accountContext.requireAccountId()).stream()
                .map(PurchaseResponse::from)
                .toList();
    }

    public List<PurchaseResponse> listByDate(LocalDate date) {
        var zone = ZoneId.systemDefault();
        var target = date == null ? LocalDate.now(zone) : date;
        var start = target.atStartOfDay(zone).toInstant();
        var end = target.plusDays(1).atStartOfDay(zone).toInstant();
        return purchaseRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(accountContext.requireAccountId(), start, end).stream()
                .map(PurchaseResponse::from)
                .toList();
    }

    @Transactional
    public PurchaseResponse create(PurchaseRequest request) {
        Product product = productRepository.findByIdAndAccountId(request.productId(), accountContext.requireAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.productId()));

        BigDecimal unitCost = request.unitCost() == null ? product.getCostPrice() : request.unitCost();
        BigDecimal totalCost = unitCost.multiply(BigDecimal.valueOf(request.quantity()));

        product.setStock(product.getStock() + request.quantity());
        product.setCostPrice(unitCost);
        inventoryService.syncProductStatus(product);

        Purchase purchase = new Purchase();
        purchase.setAccountId(product.getAccountId());
        purchase.setSupplierName(request.supplierName());
        purchase.setProductId(product.getId());
        purchase.setProductName(product.getName());
        purchase.setQuantity(request.quantity());
        purchase.setUnitCost(unitCost);
        purchase.setTotalCost(totalCost);
        purchase.setNote(request.note());

        productRepository.save(product);
        Purchase saved = purchaseRepository.save(purchase);
        inventoryService.recordMovement(product, InventoryMovementType.PURCHASE, request.quantity(), unitCost, request.note());
        return PurchaseResponse.from(saved);
    }
}
