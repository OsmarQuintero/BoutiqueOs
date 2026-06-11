package com.osmar.boutiqueos.sale;

import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.inventory.InventoryMovementType;
import com.osmar.boutiqueos.inventory.InventoryService;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleRefundRepository saleRefundRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final InventoryService inventoryService;

    public SaleService(SaleRepository saleRepository, SaleRefundRepository saleRefundRepository, ProductRepository productRepository, CustomerRepository customerRepository, InventoryService inventoryService) {
        this.saleRepository = saleRepository;
        this.saleRefundRepository = saleRefundRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.inventoryService = inventoryService;
    }

    public List<Sale> listToday() {
        var zone = ZoneId.systemDefault();
        var start = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        var end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        return saleRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }

    public List<Sale> listByDate(LocalDate date) {
        var zone = ZoneId.systemDefault();
        var target = date == null ? LocalDate.now(zone) : date;
        var start = target.atStartOfDay(zone).toInstant();
        var end = target.plusDays(1).atStartOfDay(zone).toInstant();
        return saleRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }

    public List<Sale> listAll() {
        return saleRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<SaleRefund> listRefundsToday() {
        return listRefundsByDate(null);
    }

    public List<SaleRefund> listRefundsByDate(LocalDate date) {
        var zone = ZoneId.systemDefault();
        var target = date == null ? LocalDate.now(zone) : date;
        var start = target.atStartOfDay(zone).toInstant();
        var end = target.plusDays(1).atStartOfDay(zone).toInstant();
        return saleRefundRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }

    public List<Sale> listByCustomer(Long customerId) {
        return saleRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional
    public Sale create(SaleRequest request) {
        Sale sale = new Sale();
        sale.setPaymentMethod(request.paymentMethod());
        sale.setDiscount(request.discount() == null ? BigDecimal.ZERO : request.discount());
        sale.setStatus(request.paymentMethod() == PaymentMethod.CASH ? SaleStatus.CONFIRMED : SaleStatus.PENDING);

        if (request.customerId() != null) {
            var customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.customerId()));
            sale.setCustomerId(customer.getId());
            sale.setCustomerName(customer.getName());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal estimatedProfit = BigDecimal.ZERO;

        for (SaleRequest.SaleItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemRequest.productId()));
            if (product.getStock() < itemRequest.quantity()) {
                throw new IllegalArgumentException("Not enough stock for " + product.getName());
            }

            product.setStock(product.getStock() - itemRequest.quantity());
            inventoryService.syncProductStatus(product);
            inventoryService.recordMovement(product, InventoryMovementType.SALE, -itemRequest.quantity(), product.getCostPrice(), "Venta #" + (sale.getId() == null ? "nueva" : sale.getId()));

            BigDecimal quantity = BigDecimal.valueOf(itemRequest.quantity());
            BigDecimal lineTotal = product.getSalePrice().multiply(quantity);
            BigDecimal lineCost = product.getCostPrice().multiply(quantity);

            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(product.getSalePrice());
            item.setUnitCost(product.getCostPrice());
            item.setLineTotal(lineTotal);
            sale.getItems().add(item);

            subtotal = subtotal.add(lineTotal);
            estimatedProfit = estimatedProfit.add(lineTotal.subtract(lineCost));
        }

        BigDecimal total = subtotal.subtract(sale.getDiscount()).max(BigDecimal.ZERO);
        sale.setSubtotal(subtotal);
        sale.setTotal(total);
        sale.setEstimatedProfit(estimatedProfit.subtract(sale.getDiscount()).max(BigDecimal.ZERO));

        return saleRepository.save(sale);
    }

    public List<Sale> listPending() {
        return saleRepository.findByStatus(SaleStatus.PENDING);
    }

    @Transactional
    public Sale confirm(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + id));
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new IllegalArgumentException("Cancelled sale cannot be confirmed");
        }
        if (sale.getStatus() == SaleStatus.REFUNDED) {
            throw new IllegalArgumentException("Refunded sale cannot be confirmed");
        }
        sale.setStatus(SaleStatus.CONFIRMED);
        return saleRepository.save(sale);
    }

    @Transactional
    public Sale cancel(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + id));
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            return sale;
        }
        if (sale.getStatus() == SaleStatus.CONFIRMED) {
            throw new IllegalArgumentException("Confirmed sale cannot be cancelled");
        }

        for (SaleItem item : sale.getItems()) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + item.getProductId()));
            product.setStock(product.getStock() + item.getQuantity());
            inventoryService.syncProductStatus(product);
            inventoryService.recordMovement(product, InventoryMovementType.RETURN, item.getQuantity(), item.getUnitCost(), "Cancelacion venta #" + sale.getId());
        }

        sale.setStatus(SaleStatus.CANCELLED);
        return saleRepository.save(sale);
    }

    @Transactional
    public Sale refund(Long id, SaleRefundRequest request) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + id));
        if (sale.getStatus() == SaleStatus.PENDING) {
            throw new IllegalArgumentException("Pending sale must be cancelled, not refunded");
        }
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new IllegalArgumentException("Cancelled sale cannot be refunded");
        }
        if (sale.getStatus() == SaleStatus.REFUNDED) {
            return sale;
        }

        var requestedItems = normalizeRefundRequest(sale, request);
        var remainingTotal = sale.getTotal().subtract(sale.getRefundedTotal()).max(BigDecimal.ZERO);
        var remainingProfit = sale.getEstimatedProfit().subtract(sale.getRefundedProfit()).max(BigDecimal.ZERO);

        BigDecimal refundSubtotal = BigDecimal.ZERO;
        BigDecimal refundGrossProfit = BigDecimal.ZERO;
        int totalRemainingUnitsBeforeRefund = 0;
        int totalRefundUnits = 0;
        boolean refundingAllRemaining = true;

        for (SaleItem item : sale.getItems()) {
            int remainingQty = item.getQuantity() - item.getRefundedQuantity();
            totalRemainingUnitsBeforeRefund += remainingQty;
            int refundQty = requestedItems.getOrDefault(item.getId(), 0);
            if (refundQty > 0) {
                BigDecimal qty = BigDecimal.valueOf(refundQty);
                refundSubtotal = refundSubtotal.add(item.getUnitPrice().multiply(qty));
                refundGrossProfit = refundGrossProfit.add(item.getUnitPrice().subtract(item.getUnitCost()).multiply(qty));
                totalRefundUnits += refundQty;
            }
            if (remainingQty != refundQty) {
                refundingAllRemaining = false;
            }
        }

        if (totalRefundUnits == 0) {
            throw new IllegalArgumentException("Refund needs at least one unit");
        }

        BigDecimal refundTotal;
        BigDecimal refundProfit;
        if (refundingAllRemaining) {
            refundTotal = remainingTotal;
            refundProfit = remainingProfit;
        } else {
            BigDecimal discountShare = sale.getSubtotal().compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : sale.getDiscount()
                    .multiply(refundSubtotal)
                    .divide(sale.getSubtotal(), 2, RoundingMode.HALF_UP);
            refundTotal = refundSubtotal.subtract(discountShare).max(BigDecimal.ZERO).min(remainingTotal);

            BigDecimal profitDiscountShare = totalRemainingUnitsBeforeRefund == 0
                    ? BigDecimal.ZERO
                    : remainingProfit
                    .multiply(BigDecimal.valueOf(totalRefundUnits))
                    .divide(BigDecimal.valueOf(totalRemainingUnitsBeforeRefund), 2, RoundingMode.HALF_UP);
            refundProfit = refundGrossProfit.subtract(profitDiscountShare).max(BigDecimal.ZERO).min(remainingProfit);
        }

        SaleRefund refund = new SaleRefund();
        refund.setSaleId(sale.getId());
        refund.setPaymentMethod(sale.getPaymentMethod());
        refund.setCustomerName(sale.getCustomerName());
        refund.setTotal(refundTotal);
        refund.setEstimatedProfit(refundProfit);

        for (SaleItem item : sale.getItems()) {
            int refundQty = requestedItems.getOrDefault(item.getId(), 0);
            if (refundQty == 0) {
                continue;
            }

            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + item.getProductId()));
            product.setStock(product.getStock() + refundQty);
            inventoryService.syncProductStatus(product);
            inventoryService.recordMovement(product, InventoryMovementType.RETURN, refundQty, item.getUnitCost(), "Devolucion venta #" + sale.getId());

            item.setRefundedQuantity(item.getRefundedQuantity() + refundQty);

            SaleRefundItem refundItem = new SaleRefundItem();
            refundItem.setRefund(refund);
            refundItem.setSaleItemId(item.getId());
            refundItem.setProductId(item.getProductId());
            refundItem.setProductName(item.getProductName());
            refundItem.setQuantity(refundQty);
            refundItem.setUnitPrice(item.getUnitPrice());
            refundItem.setTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(refundQty)));
            refundItem.setEstimatedProfit(item.getUnitPrice().subtract(item.getUnitCost()).multiply(BigDecimal.valueOf(refundQty)));
            refund.getItems().add(refundItem);
        }

        sale.setRefundedTotal(sale.getRefundedTotal().add(refundTotal));
        sale.setRefundedProfit(sale.getRefundedProfit().add(refundProfit));
        sale.setRefundedAt(Instant.now());
        sale.setStatus(isFullyRefunded(sale) ? SaleStatus.REFUNDED : SaleStatus.PARTIALLY_REFUNDED);
        saleRefundRepository.save(refund);
        return saleRepository.save(sale);
    }

    private Map<Long, Integer> normalizeRefundRequest(Sale sale, SaleRefundRequest request) {
        Map<Long, Integer> requested = new LinkedHashMap<>();
        if (request == null || request.items() == null || request.items().isEmpty()) {
            for (SaleItem item : sale.getItems()) {
                int remaining = item.getQuantity() - item.getRefundedQuantity();
                if (remaining > 0) {
                    requested.put(item.getId(), remaining);
                }
            }
            return requested;
        }

        for (SaleRefundRequest.Item refundItem : request.items()) {
            SaleItem item = sale.getItems().stream()
                    .filter(current -> current.getId().equals(refundItem.saleItemId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Sale item not found: " + refundItem.saleItemId()));
            int available = item.getQuantity() - item.getRefundedQuantity();
            int nextRequested = requested.getOrDefault(item.getId(), 0) + refundItem.quantity();
            if (nextRequested > available) {
                throw new IllegalArgumentException("Refund quantity exceeds available units for " + item.getProductName());
            }
            requested.put(item.getId(), nextRequested);
        }
        return requested;
    }

    private boolean isFullyRefunded(Sale sale) {
        return sale.getItems().stream().allMatch(item -> item.getRefundedQuantity() >= item.getQuantity());
    }
}
