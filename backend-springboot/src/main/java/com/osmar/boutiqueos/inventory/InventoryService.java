package com.osmar.boutiqueos.inventory;

import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.product.ProductStatus;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class InventoryService {

    private final InventoryMovementRepository movementRepository;
    private final ProductRepository productRepository;

    public InventoryService(InventoryMovementRepository movementRepository, ProductRepository productRepository) {
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
    }

    public List<InventoryMovementResponse> listRecent() {
        return movementRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(InventoryMovementResponse::from)
                .toList();
    }

    public List<InventoryMovementResponse> listByDate(LocalDate date) {
        var zone = ZoneId.systemDefault();
        var target = date == null ? LocalDate.now(zone) : date;
        var start = target.atStartOfDay(zone).toInstant();
        var end = target.plusDays(1).atStartOfDay(zone).toInstant();
        return movementRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end).stream()
                .map(InventoryMovementResponse::from)
                .toList();
    }

    @Transactional
    public Product adjustStock(InventoryAdjustmentRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.productId()));

        int nextStock = product.getStock() + request.quantityDelta();
        if (nextStock < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }

        product.setStock(nextStock);
        if (product.getStatus() != ProductStatus.ARCHIVED) {
            product.setStatus(nextStock == 0 ? ProductStatus.OUT_OF_STOCK : ProductStatus.ACTIVE);
        }

        recordMovement(product, InventoryMovementType.ADJUSTMENT, request.quantityDelta(), product.getCostPrice(), request.note());
        return productRepository.save(product);
    }

    public void recordMovement(Product product, InventoryMovementType type, int quantity, BigDecimal unitCost, String note) {
        InventoryMovement movement = new InventoryMovement();
        movement.setProductId(product.getId());
        movement.setProductName(product.getName());
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setUnitCost(unitCost);
        movement.setNote(note);
        movementRepository.save(movement);
    }

    public void syncProductStatus(Product product) {
        if (product.getStatus() != ProductStatus.ARCHIVED) {
            product.setStatus(product.getStock() == 0 ? ProductStatus.OUT_OF_STOCK : ProductStatus.ACTIVE);
        }
    }
}
