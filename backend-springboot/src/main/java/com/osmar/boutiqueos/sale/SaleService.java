package com.osmar.boutiqueos.sale;

import com.osmar.boutiqueos.config.CurrentUser;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.osmar.boutiqueos.promotion.PromotionService;
import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.customer.loyalty.LoyaltyService;
import com.osmar.boutiqueos.inventory.InventoryMovementType;
import com.osmar.boutiqueos.inventory.InventoryService;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.subscription.SubscriptionService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

    private final SaleRepository saleRepository;
    private final SaleRefundRepository saleRefundRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final InventoryService inventoryService;
    private final AccountContext accountContext;
    private final SubscriptionService subscriptionService;
    private final LoyaltyService loyaltyService;
    private final PromotionService promotionService;
    private final CurrentUser currentUser;

    public SaleService(SaleRepository saleRepository, SaleRefundRepository saleRefundRepository, ProductRepository productRepository, CustomerRepository customerRepository, InventoryService inventoryService, AccountContext accountContext, SubscriptionService subscriptionService, LoyaltyService loyaltyService, PromotionService promotionService, CurrentUser currentUser) {
        this.promotionService = promotionService;
        this.currentUser = currentUser;
        this.saleRepository = saleRepository;
        this.saleRefundRepository = saleRefundRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.inventoryService = inventoryService;
        this.accountContext = accountContext;
        this.subscriptionService = subscriptionService;
        this.loyaltyService = loyaltyService;
    }

    public List<Sale> listToday() {
        var zone = ZoneId.systemDefault();
        var start = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        var end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        return saleRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(accountContext.requireAccountId(), start, end);
    }

    public List<Sale> listByDate(LocalDate date) {
        var zone = ZoneId.systemDefault();
        var target = date == null ? LocalDate.now(zone) : date;
        var start = target.atStartOfDay(zone).toInstant();
        var end = target.plusDays(1).atStartOfDay(zone).toInstant();
        return saleRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(accountContext.requireAccountId(), start, end);
    }

    public List<Sale> listAll() {
        return saleRepository.findAllByAccountIdOrderByCreatedAtDesc(accountContext.requireAccountId());
    }

    public List<SaleRefund> listRefundsToday() {
        return listRefundsByDate(null);
    }

    public List<SaleRefund> listRefundsByDate(LocalDate date) {
        var zone = ZoneId.systemDefault();
        var target = date == null ? LocalDate.now(zone) : date;
        var start = target.atStartOfDay(zone).toInstant();
        var end = target.plusDays(1).atStartOfDay(zone).toInstant();
        return saleRefundRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(accountContext.requireAccountId(), start, end);
    }

    public List<Sale> listByCustomer(Long customerId) {
        return saleRepository.findByAccountIdAndCustomerIdOrderByCreatedAtDesc(accountContext.requireAccountId(), customerId);
    }

    @Transactional
    public Sale create(SaleRequest request) {
        subscriptionService.checkLimits("sale");
        Long accountId = accountContext.requireAccountId();
        Sale sale = new Sale();
        sale.setAccountId(accountId);
        sale.setPaymentMethod(request.paymentMethod());
        CurrentUser.Info seller = currentUser.get();
        sale.setSoldByStaffId(seller.staffUserId());
        sale.setSoldByName(seller.displayName());
        sale.setStatus(request.paymentMethod() == PaymentMethod.CASH ? SaleStatus.CONFIRMED : SaleStatus.PENDING);

        if (request.customerId() != null) {
            var customer = customerRepository.findByIdAndAccountId(request.customerId(), accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.customerId()));
            sale.setCustomerId(customer.getId());
            sale.setCustomerName(customer.getName());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal estimatedProfit = BigDecimal.ZERO;

        for (SaleRequest.SaleItemRequest itemRequest : request.items()) {
            Product product = productRepository.findByIdAndAccountId(itemRequest.productId(), accountId)
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

        applyDiscounts(sale, request, subtotal);
        BigDecimal total = subtotal.subtract(sale.getDiscount()).max(BigDecimal.ZERO);
        BigDecimal cashReceived = resolveCashReceived(sale.getPaymentMethod(), request.cashReceived(), total);
        sale.setSubtotal(subtotal);
        sale.setTotal(total);
        sale.setCashReceived(cashReceived);
        sale.setChangeDue(sale.getPaymentMethod() == PaymentMethod.CASH ? cashReceived.subtract(total).max(BigDecimal.ZERO) : BigDecimal.ZERO);
        sale.setEstimatedProfit(estimatedProfit.subtract(sale.getDiscount()).max(BigDecimal.ZERO));

        sale = saleRepository.save(sale);

        if (sale.getCustomerId() != null && sale.getStatus() == SaleStatus.CONFIRMED) {
            earnLoyaltyPoints(sale);
        }

        return sale;
    }

    /**
     * El descuento lo decide el servidor: el manual que dio la cajera y el de la
     * promocion elegida, recalculado con las reglas del servidor. Antes se
     * aceptaba el numero que mandara el navegador, fuera el que fuera.
     */
    private void applyDiscounts(Sale sale, SaleRequest request, BigDecimal subtotal) {
        boolean legacy = request.promotionId() == null && request.manualDiscount() == null;
        BigDecimal manual = legacy
                ? (request.discount() == null ? BigDecimal.ZERO : request.discount())
                : (request.manualDiscount() == null ? BigDecimal.ZERO : request.manualDiscount());
        if (manual.signum() < 0) {
            throw new IllegalArgumentException("El descuento no puede ser negativo");
        }
        manual = manual.min(subtotal).setScale(2, java.math.RoundingMode.HALF_UP);
        if (currentUser.isCashier()) {
            // Tope por cajera (lo fija la duena). Las promociones no cuentan: esas
            // ya las valida el servidor.
            int percent = currentUser.get().maxDiscountPercent() == null ? 0 : currentUser.get().maxDiscountPercent();
            BigDecimal limit = subtotal.multiply(BigDecimal.valueOf(percent))
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            if (manual.compareTo(limit) > 0) {
                throw new IllegalArgumentException("Tu usuario de caja puede dar hasta " + percent
                        + "% de descuento manual ($" + limit + "). Pide a la duena que lo autorice.");
            }
        }

        BigDecimal promo = BigDecimal.ZERO;
        if (request.promotionId() != null) {
            subscriptionService.requireFeature("promotions");
            var result = promotionService.discountFor(
                    request.promotionId(), sale.getCustomerId(), subtotal, subtotal.subtract(manual));
            promo = result.amount();
            sale.setPromotionId(result.promotion().getId());
            sale.setPromotionCode(result.promotion().getCode());
        }
        sale.setManualDiscount(manual);
        sale.setPromotionDiscount(promo);
        sale.setDiscount(manual.add(promo).min(subtotal));
    }

    /**
     * Si la cajera no captura cuanto le dieron, se toma como pago exacto para no
     * frenar la caja. Si captura menos que el total, se rechaza: antes pasaba y
     * el efectivo esperado del corte quedaba descuadrado.
     */
    private BigDecimal resolveCashReceived(PaymentMethod method, BigDecimal received, BigDecimal total) {
        if (method != PaymentMethod.CASH) {
            return BigDecimal.ZERO;
        }
        if (received == null || received.signum() == 0) {
            return total;
        }
        if (received.compareTo(total) < 0) {
            throw new IllegalArgumentException("El efectivo recibido ($" + received.setScale(2, java.math.RoundingMode.HALF_UP)
                    + ") no cubre el total ($" + total.setScale(2, java.math.RoundingMode.HALF_UP) + ")");
        }
        return received;
    }

    public List<Sale> listPending() {
        return saleRepository.findByAccountIdAndStatus(accountContext.requireAccountId(), SaleStatus.PENDING);
    }

    @Transactional
    public Sale confirm(Long id) {
        Sale sale = saleRepository.findByIdAndAccountId(id, accountContext.requireAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + id));
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new IllegalArgumentException("Cancelled sale cannot be confirmed");
        }
        if (sale.getStatus() == SaleStatus.REFUNDED) {
            throw new IllegalArgumentException("Refunded sale cannot be confirmed");
        }
        sale.setStatus(SaleStatus.CONFIRMED);
        sale = saleRepository.save(sale);

        if (sale.getCustomerId() != null) {
            earnLoyaltyPoints(sale);
        }

        return sale;
    }

    @Transactional
    public Sale cancel(Long id) {
        Sale sale = saleRepository.findByIdAndAccountId(id, accountContext.requireAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + id));
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            return sale;
        }
        if (sale.getStatus() == SaleStatus.CONFIRMED) {
            throw new IllegalArgumentException("Confirmed sale cannot be cancelled");
        }

        for (SaleItem item : sale.getItems()) {
            Product product = productRepository.findByIdAndAccountId(item.getProductId(), sale.getAccountId())
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
        Sale sale = saleRepository.findByIdAndAccountId(id, accountContext.requireAccountId())
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
        refund.setAccountId(sale.getAccountId());
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

            Product product = productRepository.findByIdAndAccountId(item.getProductId(), sale.getAccountId())
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

    /**
     * Los puntos se suman DESPUES de que la venta quedo guardada, en su propia
     * transaccion. Antes corrian dentro de la transaccion de la venta: si fallaban,
     * el try/catch se tragaba el error pero la transaccion ya estaba marcada para
     * deshacerse, y se perdia la venta completa. Asi, si los puntos fallan la venta
     * sigue en pie, y nunca se regalan puntos por una venta que no se guardo.
     */
    private void earnLoyaltyPoints(Sale sale) {
        Long saleId = sale.getId();
        Long customerId = sale.getCustomerId();
        BigDecimal total = sale.getTotal();
        String customerName = sale.getCustomerName();
        Runnable earn = () -> {
            try {
                int earned = loyaltyService.earnPoints(customerId, total, saleId);
                if (earned > 0) {
                    log.info("Venta #{}: cliente {} acumuló {} puntos", saleId, customerName, earned);
                }
            } catch (Exception e) {
                log.warn("No se pudieron acumular puntos para venta #{}: {}", saleId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    earn.run();
                }
            });
        } else {
            earn.run();
        }
    }
}
