package com.osmar.boutiqueos.report;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.config.CurrentUser;
import com.osmar.boutiqueos.config.UserRole;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.product.ProductStatus;
import com.osmar.boutiqueos.sale.PaymentMethod;
import com.osmar.boutiqueos.sale.Sale;
import com.osmar.boutiqueos.sale.SaleRefundRepository;
import com.osmar.boutiqueos.sale.SaleRefundRequest;
import com.osmar.boutiqueos.sale.SaleRepository;
import com.osmar.boutiqueos.sale.SaleRequest;
import com.osmar.boutiqueos.sale.SaleService;
import com.osmar.boutiqueos.sale.SaleStatus;
import com.osmar.boutiqueos.subscription.AccountSubscription;
import com.osmar.boutiqueos.subscription.AccountSubscriptionRepository;
import com.osmar.boutiqueos.subscription.PlanType;
import com.osmar.boutiqueos.subscription.SubscriptionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reporte por periodo, pago mixto y efectivo esperado del corte, todo con el dia
 * de la tienda (Mexico) aunque el servidor corra en UTC.
 */
@SpringBootTest
class SalesRangeReportTests {

    private static final Long ACCOUNT = 404L;
    private static final ZoneId MX = ZoneId.of("America/Mexico_City");

    @Autowired private SaleService saleService;
    @Autowired private SalesReportService reportService;
    @Autowired private DailyCashCountService cashCountService;
    @Autowired private DailyCashCountRepository cashCountRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleRefundRepository refundRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private AccountSubscriptionRepository subscriptionRepository;
    @Autowired private AccountContext accountContext;
    @Autowired private CurrentUser currentUser;

    private Product blusa;
    private Product vestido;

    @BeforeEach
    void setUp() {
        accountContext.setAccountId(ACCOUNT);
        AccountSubscription sub = new AccountSubscription();
        sub.setAccountId(ACCOUNT);
        sub.setPlan(PlanType.PRO);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(sub);
        blusa = producto("Blusa lino", "Blusas", "100.00", "300.00");
        vestido = producto("Vestido midi", "Vestidos", "200.00", "500.00");
    }

    @AfterEach
    void cleanup() {
        accountContext.clear();
        currentUser.clear();
        refundRepository.deleteAll();
        saleRepository.deleteAll();
        cashCountRepository.deleteAll();
        productRepository.deleteAll();
        subscriptionRepository.deleteAll();
    }

    private Product producto(String name, String category, String cost, String price) {
        Product p = new Product();
        p.setAccountId(ACCOUNT);
        p.setName(name);
        p.setCategory(category);
        p.setSku("RANGO-" + System.nanoTime());
        p.setCostPrice(new BigDecimal(cost));
        p.setSalePrice(new BigDecimal(price));
        p.setStock(50);
        p.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(p);
    }

    private Sale vender(PaymentMethod method, String cash, String manual, List<SaleRequest.PaymentPart> parts, Object... productQty) {
        List<SaleRequest.SaleItemRequest> items = new ArrayList<>();
        for (int i = 0; i < productQty.length; i += 2) {
            items.add(new SaleRequest.SaleItemRequest(((Product) productQty[i]).getId(), (Integer) productQty[i + 1]));
        }
        return saleService.create(new SaleRequest(method, null, cash == null ? null : new BigDecimal(cash), null, items,
                null, manual == null ? null : new BigDecimal(manual), parts));
    }

    private Sale moverA(Sale sale, LocalDateTime local) {
        Sale stored = saleRepository.findById(sale.getId()).orElseThrow();
        stored.setCreatedAt(local.atZone(MX).toInstant());
        return saleRepository.save(stored);
    }

    private static SaleRequest.PaymentPart parte(PaymentMethod method, String amount) {
        return new SaleRequest.PaymentPart(method, new BigDecimal(amount));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperaba " + expected + " y fue " + actual);
    }

    @Test
    void laVentaDeLaNocheCuentaEnSuDiaAunqueElServidorEsteEnUtc() {
        assertEquals(MX, ZoneId.systemDefault());
        Sale noche = vender(PaymentMethod.CASH, null, null, null, vestido, 1);
        // 23:30 en Mexico = 05:30 UTC del dia siguiente.
        moverA(noche, LocalDateTime.of(2026, 9, 10, 23, 30));

        SalesRangeReport dia10 = reportService.range(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
        SalesRangeReport dia11 = reportService.range(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 11));

        assertEquals(1, dia10.totals().salesCount());
        assertMoney("500", dia10.days().get(0).salesTotal());
        assertEquals(0, dia11.totals().salesCount());

        cashCountService.saveByDate(LocalDate.of(2026, 9, 10),
                new DailyCashCountRequest(BigDecimal.ZERO, new BigDecimal("500"), null));
        assertMoney("500", cashCountService.byDate(LocalDate.of(2026, 9, 10)).getExpectedCash());
    }

    @Test
    void totalesPorProductoCategoriaMetodoYQuienCobro() {
        LocalDate hoy = LocalDate.now(MX);
        LocalDate ayer = hoy.minusDays(1);

        // Ayer: 2 blusas + 1 vestido en efectivo con $110 de descuento = $990.
        Sale a = vender(PaymentMethod.CASH, null, "110", null, blusa, 2, vestido, 1);
        moverA(a, ayer.atTime(12, 0));
        // Hoy: la cajera cobra un vestido con tarjeta y la duena confirma el pago.
        currentUser.set(new CurrentUser.Info(UserRole.CASHIER, 99L, "Lupita", 100));
        Sale b = vender(PaymentMethod.CARD, null, null, null, vestido, 1);
        currentUser.clear();
        assertEquals(SaleStatus.PENDING, b.getStatus());
        saleService.confirm(b.getId());
        moverA(b, hoy.atTime(0, 1));
        // Hoy tambien: devuelven una blusa de la venta de ayer.
        Long itemBlusa = a.getItems().stream().filter(i -> i.getProductId().equals(blusa.getId())).findFirst().orElseThrow().getId();
        saleService.refund(a.getId(), new SaleRefundRequest(List.of(new SaleRefundRequest.Item(itemBlusa, 1))));
        var refund = refundRepository.findAllByAccountIdOrderByCreatedAtDesc(ACCOUNT).get(0);
        assertMoney("270", refund.getTotal());

        SalesRangeReport r = reportService.range(ayer, hoy);

        assertEquals(2, r.totals().salesCount());
        assertEquals(3, r.totals().units());
        assertMoney("1600", r.totals().grossSales());
        assertMoney("110", r.totals().discounts());
        assertMoney("1490", r.totals().salesTotal());
        assertMoney("270", r.totals().refundsTotal());
        assertMoney("1220", r.totals().netSales());
        assertMoney(new BigDecimal("590").add(new BigDecimal("300")).subtract(refund.getEstimatedProfit()).toPlainString(),
                r.totals().profit());
        assertEquals(2, r.days().size());

        Map<PaymentMethod, SalesRangeReport.MethodRow> metodos = r.paymentMethods().stream()
                .collect(Collectors.toMap(SalesRangeReport.MethodRow::method, m -> m));
        assertMoney("720", metodos.get(PaymentMethod.CASH).net());
        assertMoney("500", metodos.get(PaymentMethod.CARD).net());

        Map<String, SalesRangeReport.CategoryRow> categorias = r.categories().stream()
                .collect(Collectors.toMap(SalesRangeReport.CategoryRow::category, c -> c));
        assertEquals(1, categorias.get("Blusas").units());
        assertMoney("270", categorias.get("Blusas").revenue());
        assertEquals(2, categorias.get("Vestidos").units());
        assertMoney("950", categorias.get("Vestidos").revenue());

        Map<String, SalesRangeReport.SellerRow> quien = r.sellers().stream()
                .collect(Collectors.toMap(SalesRangeReport.SellerRow::seller, s -> s));
        assertMoney("990", quien.get("Dueña").salesTotal());
        assertMoney("500", quien.get("Lupita").salesTotal());
    }

    @Test
    void pagoMixtoGuardaElDesgloseYSoloElEfectivoEntraAlCorte() {
        Sale mixta = vender(PaymentMethod.MIXED, "500", null,
                List.of(parte(PaymentMethod.CASH, "300"), parte(PaymentMethod.CARD, "200")), vestido, 1);

        assertEquals(2, mixta.getPayments().size());
        assertMoney("200", mixta.getChangeDue());
        assertEquals(SaleStatus.PENDING, mixta.getStatus());
        saleService.confirm(mixta.getId());

        LocalDate hoy = LocalDate.now(MX);
        cashCountService.saveByDate(hoy, new DailyCashCountRequest(new BigDecimal("100"), new BigDecimal("400"), null));
        assertMoney("400", cashCountService.byDate(hoy).getExpectedCash());

        SalesRangeReport r = reportService.range(hoy, hoy);
        Map<PaymentMethod, SalesRangeReport.MethodRow> metodos = r.paymentMethods().stream()
                .collect(Collectors.toMap(SalesRangeReport.MethodRow::method, m -> m));
        assertMoney("300", metodos.get(PaymentMethod.CASH).salesTotal());
        assertMoney("200", metodos.get(PaymentMethod.CARD).salesTotal());
    }

    @Test
    void pagoMixtoQueNoCuadraSeRechaza() {
        var noSuma = assertThrows(IllegalArgumentException.class, () -> vender(PaymentMethod.MIXED, null, null,
                List.of(parte(PaymentMethod.CASH, "300"), parte(PaymentMethod.CARD, "100")), vestido, 1));
        assertTrue(noSuma.getMessage().contains("Deben coincidir"));

        var unSoloMetodo = assertThrows(IllegalArgumentException.class, () -> vender(PaymentMethod.MIXED, null, null,
                List.of(parte(PaymentMethod.CASH, "500")), vestido, 1));
        assertTrue(unSoloMetodo.getMessage().contains("al menos dos"));

        var efectivoCorto = assertThrows(IllegalArgumentException.class, () -> vender(PaymentMethod.MIXED, "200", null,
                List.of(parte(PaymentMethod.CASH, "300"), parte(PaymentMethod.TRANSFER, "200")), vestido, 1));
        assertTrue(efectivoCorto.getMessage().contains("la parte en efectivo"));
    }

    @Test
    void elCorteCuentaLaVentaCompletaAunqueHayaCambioYAunqueTengaDevolucion() {
        // $500 pagados con $1,000: a la caja entran $500 (antes contaba $0).
        vender(PaymentMethod.CASH, "1000", null, null, vestido, 1);
        // 2 blusas ($600) y devuelven una ($300): la venta sigue contando.
        Sale blusas = vender(PaymentMethod.CASH, null, null, null, blusa, 2);
        saleService.refund(blusas.getId(),
                new SaleRefundRequest(List.of(new SaleRefundRequest.Item(blusas.getItems().get(0).getId(), 1))));

        LocalDate hoy = LocalDate.now(MX);
        cashCountService.saveByDate(hoy, new DailyCashCountRequest(BigDecimal.ZERO, new BigDecimal("800"), null));
        assertMoney("800", cashCountService.byDate(hoy).getExpectedCash());
    }

    @Test
    void elPeriodoSeValida() {
        assertThrows(IllegalArgumentException.class,
                () -> reportService.range(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> reportService.range(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 9, 1)));
    }
}
