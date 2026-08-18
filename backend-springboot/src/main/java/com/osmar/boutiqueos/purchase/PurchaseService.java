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
        inventoryService.recordMovement(product, InventoryMovementType.PURCHASE, request.quantity(), unitCost, request.note(), saved.getId());
        return PurchaseResponse.from(saved);
    }

    @Transactional
    public PurchaseResponse update(Long id, PurchaseRequest request) {
        Long accountId = accountContext.requireAccountId();
        Purchase purchase = purchaseRepository.findById(id)
                .filter(p -> p.getAccountId().equals(accountId))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Compra no encontrada"));

        Product product = productRepository.findByIdAndAccountId(purchase.getProductId(), accountId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + purchase.getProductId()));

        int oldQty = purchase.getQuantity();
        int newQty = request.quantity();
        BigDecimal oldCost = purchase.getUnitCost();
        BigDecimal newUnitCost = request.unitCost() == null ? oldCost : request.unitCost();

        product.setStock(product.getStock() - oldQty + newQty);
        product.setCostPrice(newUnitCost);
        inventoryService.syncProductStatus(product);

        purchase.setSupplierName(request.supplierName());
        purchase.setQuantity(newQty);
        purchase.setUnitCost(newUnitCost);
        purchase.setTotalCost(newUnitCost.multiply(BigDecimal.valueOf(newQty)));
        purchase.setNote(request.note());

        inventoryService.removeMovementBySource(accountId, purchase.getId());
        productRepository.save(product);
        Purchase saved = purchaseRepository.save(purchase);
        inventoryService.recordMovement(product, InventoryMovementType.PURCHASE, newQty, newUnitCost, request.note(), saved.getId());
        return PurchaseResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        Long accountId = accountContext.requireAccountId();
        Purchase purchase = purchaseRepository.findById(id)
                .filter(p -> p.getAccountId().equals(accountId))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Compra no encontrada"));

        Product product = productRepository.findByIdAndAccountId(purchase.getProductId(), accountId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + purchase.getProductId()));

        product.setStock(product.getStock() - purchase.getQuantity());
        if (product.getStock() < 0) {
            throw new IllegalArgumentException("No se puede eliminar: el stock quedaría negativo");
        }
        inventoryService.syncProductStatus(product);

        inventoryService.removeMovementBySource(accountId, purchase.getId());
        productRepository.save(product);
        purchaseRepository.deleteById(id);
    }
}
