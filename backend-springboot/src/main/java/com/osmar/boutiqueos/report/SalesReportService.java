package com.osmar.boutiqueos.report;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.sale.PaymentMethod;
import com.osmar.boutiqueos.sale.Sale;
import com.osmar.boutiqueos.sale.SaleItem;
import com.osmar.boutiqueos.sale.SalePayment;
import com.osmar.boutiqueos.sale.SaleRefund;
import com.osmar.boutiqueos.sale.SaleRefundItem;
import com.osmar.boutiqueos.sale.SaleRefundRepository;
import com.osmar.boutiqueos.sale.SaleRepository;
import com.osmar.boutiqueos.sale.SaleStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ventas de un periodo: totales, por dia, por metodo, por producto, por categoria y por quien cobro. */
@Service
public class SalesReportService {

    static final int MAX_DAYS = 366;
    private static final int MAX_PRODUCTS = 100;
    private static final String NO_CATEGORY = "Sin categoría";
    private static final String OWNER = "Dueña";
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Set<SaleStatus> COUNTED =
            EnumSet.of(SaleStatus.CONFIRMED, SaleStatus.PARTIALLY_REFUNDED, SaleStatus.REFUNDED);

    private final SaleRepository saleRepository;
    private final SaleRefundRepository saleRefundRepository;
    private final ProductRepository productRepository;
    private final AccountContext accountContext;

    public SalesReportService(
            SaleRepository saleRepository,
            SaleRefundRepository saleRefundRepository,
            ProductRepository productRepository,
            AccountContext accountContext
    ) {
        this.saleRepository = saleRepository;
        this.saleRefundRepository = saleRefundRepository;
        this.productRepository = productRepository;
        this.accountContext = accountContext;
    }

    @Transactional(readOnly = true)
    public SalesRangeReport range(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Indica la fecha inicial y la final");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new IllegalArgumentException("El periodo puede ser de hasta un año");
        }

        Long accountId = accountContext.requireAccountId();
        ZoneId zone = ZoneId.systemDefault();
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        List<Sale> sales = saleRepository
                .findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(accountId, start, end);
        List<SaleRefund> refunds = saleRefundRepository
                .findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(accountId, start, end);
        Map<Long, String> categoryByProduct = new HashMap<>();
        for (Product product : productRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId)) {
            String category = product.getCategory();
            categoryByProduct.put(product.getId(), category == null || category.isBlank() ? NO_CATEGORY : category.trim());
        }

        Map<LocalDate, DayAcc> days = new LinkedHashMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.put(day, new DayAcc());
        }
        Map<PaymentMethod, BigDecimal[]> methods = new EnumMap<>(PaymentMethod.class);
        Map<String, ProductAcc> products = new LinkedHashMap<>();
        Map<String, SellerAcc> sellers = new LinkedHashMap<>();

        int salesCount = 0;
        int units = 0;
        int pendingCount = 0;
        BigDecimal gross = ZERO;
        BigDecimal discounts = ZERO;
        BigDecimal salesTotal = ZERO;
        BigDecimal profit = ZERO;
        BigDecimal pendingTotal = ZERO;

        for (Sale sale : sales) {
            if (sale.getStatus() == SaleStatus.PENDING) {
                pendingCount++;
                pendingTotal = pendingTotal.add(sale.getTotal());
                continue;
            }
            if (!COUNTED.contains(sale.getStatus())) {
                continue;
            }
            salesCount++;
            gross = gross.add(sale.getSubtotal());
            discounts = discounts.add(sale.getDiscount());
            salesTotal = salesTotal.add(sale.getTotal());
            profit = profit.add(sale.getEstimatedProfit());

            DayAcc day = days.get(sale.getCreatedAt().atZone(zone).toLocalDate());
            if (day != null) {
                day.count++;
                day.sales = day.sales.add(sale.getTotal());
            }
            for (SalePayment part : paymentsOf(sale)) {
                BigDecimal[] row = methods.computeIfAbsent(part.getMethod(), key -> new BigDecimal[]{ZERO, ZERO});
                row[0] = row[0].add(part.getAmount());
            }

            // El descuento de la venta se reparte entre sus lineas en proporcion.
            BigDecimal factor = sale.getSubtotal().signum() > 0
                    ? sale.getTotal().divide(sale.getSubtotal(), 10, RoundingMode.HALF_UP)
                    : ZERO;
            for (SaleItem item : sale.getItems()) {
                units += item.getQuantity();
                ProductAcc acc = products.computeIfAbsent(productKey(item.getProductId(), item.getProductName()),
                        key -> new ProductAcc(item.getProductId(), item.getProductName(),
                                categoryByProduct.getOrDefault(item.getProductId(), NO_CATEGORY)));
                BigDecimal cost = item.getUnitCost() == null ? ZERO : item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity()));
                acc.units += item.getQuantity();
                acc.revenue = acc.revenue.add(item.getLineTotal().multiply(factor));
                acc.cost = acc.cost.add(cost);
            }

            // Ventas viejas: sin nombre o con "Duena" sin tilde.
            String seller = sale.getSoldByName() == null || sale.getSoldByName().isBlank() || "Duena".equals(sale.getSoldByName())
                    ? OWNER : sale.getSoldByName();
            SellerAcc sellerAcc = sellers.computeIfAbsent(seller, SellerAcc::new);
            sellerAcc.count++;
            sellerAcc.total = sellerAcc.total.add(sale.getTotal());
        }

        BigDecimal refundsTotal = ZERO;
        for (SaleRefund refund : refunds) {
            refundsTotal = refundsTotal.add(refund.getTotal());
            profit = profit.subtract(refund.getEstimatedProfit());
            DayAcc day = days.get(refund.getCreatedAt().atZone(zone).toLocalDate());
            if (day != null) {
                day.refunds = day.refunds.add(refund.getTotal());
            }
            BigDecimal[] row = methods.computeIfAbsent(refund.getPaymentMethod(), key -> new BigDecimal[]{ZERO, ZERO});
            row[1] = row[1].add(refund.getTotal());

            BigDecimal itemsTotal = refund.getItems().stream().map(SaleRefundItem::getTotal).reduce(ZERO, BigDecimal::add);
            BigDecimal factor = itemsTotal.signum() > 0
                    ? refund.getTotal().divide(itemsTotal, 10, RoundingMode.HALF_UP)
                    : ZERO;
            for (SaleRefundItem item : refund.getItems()) {
                units -= item.getQuantity();
                ProductAcc acc = products.computeIfAbsent(productKey(item.getProductId(), item.getProductName()),
                        key -> new ProductAcc(item.getProductId(), item.getProductName(),
                                categoryByProduct.getOrDefault(item.getProductId(), NO_CATEGORY)));
                acc.units -= item.getQuantity();
                acc.revenue = acc.revenue.subtract(item.getTotal().multiply(factor));
                // total - utilidad bruta = costo de lo devuelto
                acc.cost = acc.cost.subtract(item.getTotal().subtract(item.getEstimatedProfit()));
            }
        }

        BigDecimal netSales = salesTotal.subtract(refundsTotal);
        SalesRangeReport.Totals totals = new SalesRangeReport.Totals(
                salesCount,
                units,
                money(gross),
                money(discounts),
                money(salesTotal),
                money(refundsTotal),
                money(netSales),
                money(profit),
                salesCount > 0 ? salesTotal.divide(BigDecimal.valueOf(salesCount), 2, RoundingMode.HALF_UP) : money(ZERO),
                netSales.signum() > 0 ? profit.multiply(HUNDRED).divide(netSales, 2, RoundingMode.HALF_UP) : money(ZERO),
                pendingCount,
                money(pendingTotal)
        );

        List<SalesRangeReport.DayRow> dayRows = new ArrayList<>();
        days.forEach((date, acc) -> dayRows.add(new SalesRangeReport.DayRow(
                date, acc.count, money(acc.sales), money(acc.refunds), money(acc.sales.subtract(acc.refunds)))));

        List<SalesRangeReport.MethodRow> methodRows = new ArrayList<>();
        methods.forEach((method, row) -> methodRows.add(new SalesRangeReport.MethodRow(
                method, money(row[0]), money(row[1]), money(row[0].subtract(row[1])))));

        List<ProductAcc> productAccs = products.values().stream()
                .filter(acc -> acc.units != 0 || acc.revenue.signum() != 0)
                .sorted(Comparator.comparing((ProductAcc acc) -> acc.revenue).reversed())
                .toList();
        List<SalesRangeReport.ProductRow> productRows = productAccs.stream()
                .limit(MAX_PRODUCTS)
                .map(acc -> new SalesRangeReport.ProductRow(acc.productId, acc.name, acc.category, acc.units,
                        money(acc.revenue), money(acc.revenue.subtract(acc.cost))))
                .toList();

        Map<String, ProductAcc> byCategory = new LinkedHashMap<>();
        for (ProductAcc acc : productAccs) {
            ProductAcc cat = byCategory.computeIfAbsent(acc.category, key -> new ProductAcc(null, key, key));
            cat.units += acc.units;
            cat.revenue = cat.revenue.add(acc.revenue);
            cat.cost = cat.cost.add(acc.cost);
        }
        BigDecimal categoryRevenue = byCategory.values().stream().map(acc -> acc.revenue).reduce(ZERO, BigDecimal::add);
        List<SalesRangeReport.CategoryRow> categoryRows = byCategory.values().stream()
                .sorted(Comparator.comparing((ProductAcc acc) -> acc.revenue).reversed())
                .map(acc -> new SalesRangeReport.CategoryRow(acc.category, acc.units, money(acc.revenue),
                        money(acc.revenue.subtract(acc.cost)),
                        categoryRevenue.signum() > 0
                                ? acc.revenue.multiply(HUNDRED).divide(categoryRevenue, 2, RoundingMode.HALF_UP)
                                : money(ZERO)))
                .toList();

        List<SalesRangeReport.SellerRow> sellerRows = sellers.values().stream()
                .sorted(Comparator.comparing((SellerAcc acc) -> acc.total).reversed())
                .map(acc -> new SalesRangeReport.SellerRow(acc.name, acc.count, money(acc.total)))
                .toList();

        return new SalesRangeReport(from, to, totals, dayRows, methodRows, productRows, categoryRows, sellerRows);
    }

    /** Pago mixto: sus partes. Cualquier otra venta: todo el total con su metodo. */
    static List<SalePayment> paymentsOf(Sale sale) {
        if (sale.getPaymentMethod() == PaymentMethod.MIXED && !sale.getPayments().isEmpty()) {
            return sale.getPayments();
        }
        return List.of(new SalePayment(sale.getPaymentMethod(), sale.getTotal()));
    }

    private static String productKey(Long productId, String name) {
        return productId != null ? "id:" + productId : "name:" + name;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class DayAcc {
        int count;
        BigDecimal sales = ZERO;
        BigDecimal refunds = ZERO;
    }

    private static final class ProductAcc {
        final Long productId;
        final String name;
        final String category;
        int units;
        BigDecimal revenue = ZERO;
        BigDecimal cost = ZERO;

        ProductAcc(Long productId, String name, String category) {
            this.productId = productId;
            this.name = name;
            this.category = category;
        }
    }

    private static final class SellerAcc {
        final String name;
        int count;
        BigDecimal total = ZERO;

        SellerAcc(String name) {
            this.name = name;
        }
    }
}
