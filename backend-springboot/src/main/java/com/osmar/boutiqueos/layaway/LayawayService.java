package com.osmar.boutiqueos.layaway;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.config.CurrentUser;
import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.inventory.InventoryMovementType;
import com.osmar.boutiqueos.inventory.InventoryService;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.sale.LayawaySale;
import com.osmar.boutiqueos.sale.PaymentMethod;
import com.osmar.boutiqueos.sale.SaleService;
import com.osmar.boutiqueos.subscription.SubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Apartados.
 *
 * <p>Reglas de dinero, para que el corte cuadre: cada abono en efectivo entra al
 * corte del dia en que se recibe. Al liquidar se registra la venta (para
 * reportes, puntos y devoluciones), pero marcada con el apartado para que su
 * efectivo no se cuente dos veces. El stock se descuenta al apartar, no al liquidar.
 */
@Service
public class LayawayService {

    static final int DEFAULT_DAYS = 30;

    private final LayawayRepository layawayRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final InventoryService inventoryService;
    private final SaleService saleService;
    private final SubscriptionService subscriptionService;
    private final AccountContext accountContext;
    private final CurrentUser currentUser;

    public LayawayService(
            LayawayRepository layawayRepository,
            ProductRepository productRepository,
            CustomerRepository customerRepository,
            InventoryService inventoryService,
            SaleService saleService,
            SubscriptionService subscriptionService,
            AccountContext accountContext,
            CurrentUser currentUser
    ) {
        this.layawayRepository = layawayRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.inventoryService = inventoryService;
        this.saleService = saleService;
        this.subscriptionService = subscriptionService;
        this.accountContext = accountContext;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<Layaway> list(LayawayStatus status) {
        subscriptionService.requireFeature("layaways");
        Long accountId = accountContext.requireAccountId();
        return status == null
                ? layawayRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId)
                : layawayRepository.findAllByAccountIdAndStatusOrderByCreatedAtDesc(accountId, status);
    }

    @Transactional(readOnly = true)
    public Layaway get(Long id) {
        subscriptionService.requireFeature("layaways");
        return find(id);
    }

    @Transactional
    public Layaway create(LayawayRequests.Create request) {
        subscriptionService.requireFeature("layaways");
        Long accountId = accountContext.requireAccountId();
        var customer = customerRepository.findByIdAndAccountId(request.customerId(), accountId)
                .orElseThrow(() -> new IllegalArgumentException("Elige una clienta registrada: el apartado va a su nombre"));

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate dueDate = request.dueDate() == null ? today.plusDays(DEFAULT_DAYS) : request.dueDate();
        if (dueDate.isBefore(today)) {
            throw new IllegalArgumentException("La fecha limite no puede ser anterior a hoy");
        }

        Layaway layaway = new Layaway();
        layaway.setAccountId(accountId);
        layaway.setCustomerId(customer.getId());
        layaway.setCustomerName(customer.getName());
        layaway.setDueDate(dueDate);
        layaway.setNotes(clean(request.notes()));
        layaway.setCreatedByName(currentUser.get().displayName());

        // La misma prenda dos veces en la lista se junta en una linea.
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (LayawayRequests.Item item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        Map<Product, Integer> reserved = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = productRepository.findByIdAndAccountId(entry.getKey(), accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + entry.getKey()));
            int quantity = entry.getValue();
            if (product.getStock() < quantity) {
                throw new IllegalArgumentException("No hay suficiente stock de " + product.getName()
                        + " (quedan " + product.getStock() + ")");
            }
            BigDecimal lineTotal = product.getSalePrice().multiply(BigDecimal.valueOf(quantity));
            LayawayItem item = new LayawayItem();
            item.setLayaway(layaway);
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setQuantity(quantity);
            item.setUnitPrice(product.getSalePrice());
            item.setUnitCost(product.getCostPrice());
            item.setLineTotal(lineTotal);
            layaway.getItems().add(item);
            total = total.add(lineTotal);
            reserved.put(product, quantity);
        }
        layaway.setTotal(total);

        BigDecimal deposit = money(request.deposit().amount());
        requireSingleMethod(request.deposit().method());
        if (deposit.compareTo(total) > 0) {
            throw new IllegalArgumentException("El anticipo ($" + deposit + ") no puede ser mayor que el total ($" + money(total) + ")");
        }

        layaway = layawayRepository.save(layaway);
        for (Map.Entry<Product, Integer> entry : reserved.entrySet()) {
            Product product = entry.getKey();
            product.setStock(product.getStock() - entry.getValue());
            inventoryService.syncProductStatus(product);
            productRepository.save(product);
            inventoryService.recordMovement(product, InventoryMovementType.LAYAWAY, -entry.getValue(),
                    product.getCostPrice(), "Apartado #" + layaway.getId() + " de " + layaway.getCustomerName());
        }

        addPayment(layaway, request.deposit().method(), deposit, noteOr(request.deposit().note(), "Anticipo"));
        completeIfPaid(layaway);
        return layawayRepository.save(layaway);
    }

    @Transactional
    public Layaway pay(Long id, LayawayRequests.Payment request) {
        subscriptionService.requireFeature("layaways");
        Layaway layaway = find(id);
        requireOpen(layaway);
        requireSingleMethod(request.method());
        BigDecimal amount = money(request.amount());
        if (amount.compareTo(layaway.getRemaining()) > 0) {
            throw new IllegalArgumentException("El abono ($" + amount + ") es mayor que lo que falta ($"
                    + money(layaway.getRemaining()) + ")");
        }
        addPayment(layaway, request.method(), amount, noteOr(request.note(), "Abono"));
        completeIfPaid(layaway);
        return layawayRepository.save(layaway);
    }

    @Transactional
    public Layaway cancel(Long id, LayawayRequests.Cancel request) {
        subscriptionService.requireFeature("layaways");
        Layaway layaway = find(id);
        requireOpen(layaway);
        Long accountId = layaway.getAccountId();
        for (LayawayItem item : layaway.getItems()) {
            if (item.getProductId() == null) {
                continue;
            }
            // Si la prenda se borro del catalogo ya no hay a donde regresarla.
            productRepository.findByIdAndAccountId(item.getProductId(), accountId).ifPresent(product -> {
                product.setStock(product.getStock() + item.getQuantity());
                inventoryService.syncProductStatus(product);
                productRepository.save(product);
                inventoryService.recordMovement(product, InventoryMovementType.RETURN, item.getQuantity(),
                        item.getUnitCost(), "Cancelacion apartado #" + layaway.getId());
            });
        }
        if (request != null && request.refundPayments() && layaway.getPaid().signum() > 0) {
            // Se devuelve en efectivo: es lo que sale de la caja hoy.
            addPayment(layaway, PaymentMethod.CASH, layaway.getPaid().negate(), "Devolucion de lo abonado");
            layaway.setRefunded(layaway.getPaid());
        }
        layaway.setStatus(LayawayStatus.CANCELLED);
        layaway.setCancelledAt(Instant.now());
        layaway.setCancelReason(request == null ? null : clean(request.reason()));
        return layawayRepository.save(layaway);
    }

    /** Abonos y devoluciones del dia, para el corte. */
    @Transactional(readOnly = true)
    public List<LayawayPayment> paymentsOn(LocalDate date) {
        subscriptionService.requireFeature("layaways");
        ZoneId zone = ZoneId.systemDefault();
        LocalDate target = date == null ? LocalDate.now(zone) : date;
        return layawayRepository.findPayments(accountContext.requireAccountId(),
                target.atStartOfDay(zone).toInstant(), target.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private void completeIfPaid(Layaway layaway) {
        if (layaway.getRemaining().signum() > 0) {
            return;
        }
        Map<PaymentMethod, BigDecimal> byMethod = new EnumMap<>(PaymentMethod.class);
        for (LayawayPayment payment : layaway.getPayments()) {
            if (payment.getAmount().signum() > 0) {
                byMethod.merge(payment.getMethod(), payment.getAmount(), BigDecimal::add);
            }
        }
        List<LayawaySale.Line> lines = layaway.getItems().stream()
                .map(item -> new LayawaySale.Line(item.getProductId(), item.getProductName(), item.getQuantity(),
                        item.getUnitPrice(), item.getUnitCost()))
                .toList();
        var sale = saleService.recordLayawaySale(new LayawaySale(layaway.getId(), layaway.getCustomerId(),
                layaway.getCustomerName(), currentUser.get().displayName(), lines, byMethod));
        layaway.setSaleId(sale.getId());
        layaway.setStatus(LayawayStatus.COMPLETED);
        layaway.setCompletedAt(Instant.now());
    }

    private void addPayment(Layaway layaway, PaymentMethod method, BigDecimal amount, String note) {
        LayawayPayment payment = new LayawayPayment();
        payment.setLayaway(layaway);
        payment.setMethod(method);
        payment.setAmount(amount);
        payment.setReceivedByName(currentUser.get().displayName());
        payment.setNote(note);
        layaway.getPayments().add(payment);
        if (amount.signum() > 0) {
            layaway.setPaid(layaway.getPaid().add(amount));
        }
    }

    private Layaway find(Long id) {
        return layawayRepository.findByIdAndAccountId(id, accountContext.requireAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Apartado no encontrado: " + id));
    }

    private static void requireOpen(Layaway layaway) {
        if (layaway.getStatus() == LayawayStatus.COMPLETED) {
            throw new IllegalArgumentException("Este apartado ya esta liquidado");
        }
        if (layaway.getStatus() == LayawayStatus.CANCELLED) {
            throw new IllegalArgumentException("Este apartado esta cancelado");
        }
    }

    private static void requireSingleMethod(PaymentMethod method) {
        if (method == null || method == PaymentMethod.MIXED) {
            throw new IllegalArgumentException("Cada abono va con un solo metodo de pago");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String noteOr(String note, String fallback) {
        String clean = clean(note);
        return clean == null ? fallback : clean;
    }
}
