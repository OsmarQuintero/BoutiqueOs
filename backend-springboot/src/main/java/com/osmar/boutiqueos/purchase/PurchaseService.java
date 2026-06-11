package com.osmar.boutiqueos.purchase;

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

    public PurchaseService(PurchaseRepository purchaseRepository, ProductRepository productRepository, InventoryService inventoryService) {
        this.purchaseRepository = purchaseRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
    }

    public List<PurchaseResponse> listRecent() {
        return purchaseRepository.findTop30ByOrderByCreatedAtDesc().stream()
                .map(PurchaseResponse::from)
                .toList();
    }

    public List<PurchaseResponse> listByDate(LocalDate date) {
        var zone = ZoneId.systemDefault();
        var target = date == null ? LocalDate.now(zone) : date;
        var start = target.atStartOfDay(zone).toInstant();
        var end = target.plusDays(1).atStartOfDay(zone).toInstant();
        return purchaseRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end).stream()
                .map(PurchaseResponse::from)
                .toList();
    }

    @Transactional
    public PurchaseResponse create(PurchaseRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.productId()));

        BigDecimal unitCost = request.unitCost() == null ? product.getCostPrice() : request.unitCost();
        BigDecimal totalCost = unitCost.multiply(BigDecimal.valueOf(request.quantity()));

        product.setStock(product.getStock() + request.quantity());
        product.setCostPrice(unitCost);
        inventoryService.syncProductStatus(product);

        Purchase purchase = new Purchase();
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
