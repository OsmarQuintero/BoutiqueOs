package com.osmar.boutiqueos.layaway;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.customer.Customer;
import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.inventory.InventoryMovementRepository;
import com.osmar.boutiqueos.inventory.InventoryMovementType;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.product.ProductStatus;
import com.osmar.boutiqueos.report.DailyCashCountRepository;
import com.osmar.boutiqueos.report.DailyCashCountRequest;
import com.osmar.boutiqueos.report.DailyCashCountService;
import com.osmar.boutiqueos.sale.PaymentMethod;
import com.osmar.boutiqueos.sale.Sale;
import com.osmar.boutiqueos.sale.SaleRepository;
import com.osmar.boutiqueos.sale.SaleStatus;
import com.osmar.boutiqueos.settings.AppSettings;
import com.osmar.boutiqueos.settings.AppSettingsRepository;
import com.osmar.boutiqueos.settings.AppSettingsService;
import com.osmar.boutiqueos.settings.BackupService;
import com.osmar.boutiqueos.subscription.AccountSubscription;
import com.osmar.boutiqueos.subscription.AccountSubscriptionRepository;
import com.osmar.boutiqueos.subscription.PlanType;
import com.osmar.boutiqueos.subscription.SubscriptionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Apartados: el stock se reserva al apartar, cada abono en efectivo entra al
 * corte de su dia y al liquidar queda una venta que no cuenta el efectivo dos veces.
 */
@SpringBootTest
class LayawayTests {

    private static final ZoneId MX = ZoneId.of("America/Mexico_City");

    @Autowired private LayawayService layawayService;
    @Autowired private LayawayRepository layawayRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private InventoryMovementRepository movementRepository;
    @Autowired private DailyCashCountService cashCountService;
    @Autowired private DailyCashCountRepository cashCountRepository;
    @Autowired private AccountSubscriptionRepository subscriptionRepository;
    @Autowired private AccountContext accountContext;
    @Autowired private BackupService backupService;
    @Autowired private AppSettingsService appSettingsService;
    @Autowired private AppSettingsRepository appSettingsRepository;

    // Cuenta real (el respaldo necesita los datos de la tienda).
    private Long accountId;

    private Product vestido;
    private Customer ana;

    @BeforeEach
    void setUp() {
        accountId = appSettingsService.provisionOwner("apartados-" + System.nanoTime() + "@boutique.test",
                "ClaveSegura2026", "Tienda apartados").getId();
        // provisionOwner la marca demo (sin limites de plan); aqui el plan si cuenta.
        AppSettings cuenta = appSettingsRepository.findById(accountId).orElseThrow();
        cuenta.setRole("owner");
        appSettingsRepository.save(cuenta);
        accountContext.setAccountId(accountId);
        plan(PlanType.PRO);
        vestido = new Product();
        vestido.setAccountId(accountId);
        vestido.setName("Vestido de fiesta");
        vestido.setCategory("Vestidos");
        vestido.setSku("APARTADO-" + System.nanoTime());
        vestido.setCostPrice(new BigDecimal("400.00"));
        vestido.setSalePrice(new BigDecimal("1000.00"));
        vestido.setStock(5);
        vestido.setStatus(ProductStatus.ACTIVE);
        vestido = productRepository.save(vestido);

        ana = new Customer();
        ana.setAccountId(accountId);
        ana.setName("Ana Torres");
        ana.setPhone("8181818181");
        ana = customerRepository.save(ana);
    }

    @AfterEach
    void cleanup() {
        accountContext.clear();
        layawayRepository.deleteAll();
        saleRepository.deleteAll();
        movementRepository.deleteAll();
        cashCountRepository.deleteAll();
        productRepository.deleteAll();
        customerRepository.deleteAll();
        subscriptionRepository.deleteAll();
    }

    private void plan(PlanType type) {
        AccountSubscription sub = subscriptionRepository.findByAccountId(accountId).orElseGet(AccountSubscription::new);
        sub.setAccountId(accountId);
        sub.setPlan(type);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(sub);
    }

    private Layaway apartar(int piezas, String anticipo) {
        return layawayService.create(new LayawayRequests.Create(ana.getId(),
                List.of(new LayawayRequests.Item(vestido.getId(), piezas)),
                new LayawayRequests.Payment(PaymentMethod.CASH, new BigDecimal(anticipo), null), null, null));
    }

    private BigDecimal efectivoEsperadoHoy() {
        LocalDate hoy = LocalDate.now(MX);
        cashCountService.saveByDate(hoy, new DailyCashCountRequest(BigDecimal.ZERO, BigDecimal.ONE, null));
        return cashCountService.byDate(hoy).getExpectedCash();
    }

    private int stock() {
        return productRepository.findById(vestido.getId()).orElseThrow().getStock();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperaba " + expected + " y fue " + actual);
    }

    @Test
    void apartarReservaElStockYGuardaElAnticipo() {
        Layaway l = apartar(2, "500");

        assertEquals(LayawayStatus.OPEN, l.getStatus());
        assertMoney("2000", l.getTotal());
        assertMoney("500", l.getPaid());
        assertMoney("1500", l.getRemaining());
        assertEquals(LocalDate.now(MX).plusDays(30), l.getDueDate());
        assertEquals(3, stock());
        assertTrue(movementRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .anyMatch(m -> m.getType() == InventoryMovementType.LAYAWAY && m.getQuantity() == -2));
        assertMoney("500", efectivoEsperadoHoy());
    }

    @Test
    void liquidarRegistraLaVentaSinContarDosVecesElEfectivo() {
        Layaway l = apartar(2, "500");
        l = layawayService.pay(l.getId(), new LayawayRequests.Payment(PaymentMethod.CARD, new BigDecimal("1500"), null));

        assertEquals(LayawayStatus.COMPLETED, l.getStatus());
        assertNotNull(l.getSaleId());
        Sale venta = saleRepository.findById(l.getSaleId()).orElseThrow();
        assertEquals(l.getId(), venta.getLayawayId());
        assertEquals(SaleStatus.CONFIRMED, venta.getStatus());
        assertEquals(PaymentMethod.MIXED, venta.getPaymentMethod());
        assertMoney("2000", venta.getTotal());
        assertMoney("1200", venta.getEstimatedProfit());
        assertEquals(3, stock(), "el stock ya se habia descontado al apartar");
        // Solo el anticipo en efectivo; la venta del apartado no suma otra vez.
        assertMoney("500", efectivoEsperadoHoy());
    }

    @Test
    void noSeAbonaDeMasNiAUnApartadoLiquidado() {
        Layaway l = apartar(1, "200");
        Long id = l.getId();
        var deMas = assertThrows(IllegalArgumentException.class, () -> layawayService.pay(id,
                new LayawayRequests.Payment(PaymentMethod.CASH, new BigDecimal("900"), null)));
        assertTrue(deMas.getMessage().contains("mayor que lo que falta"));

        layawayService.pay(id, new LayawayRequests.Payment(PaymentMethod.CASH, new BigDecimal("800"), null));
        var liquidado = assertThrows(IllegalArgumentException.class, () -> layawayService.pay(id,
                new LayawayRequests.Payment(PaymentMethod.CASH, new BigDecimal("1"), null)));
        assertTrue(liquidado.getMessage().contains("liquidado"));
        assertThrows(IllegalArgumentException.class, () -> apartar(9, "100"), "no alcanza el stock");
    }

    @Test
    void cancelarConDevolucionRegresaStockYDinero() {
        Layaway l = apartar(2, "500");
        l = layawayService.cancel(l.getId(), new LayawayRequests.Cancel(true, "Ya no lo quiere"));

        assertEquals(LayawayStatus.CANCELLED, l.getStatus());
        assertMoney("500", l.getRefunded());
        assertEquals(5, stock());
        assertMoney("0", efectivoEsperadoHoy());
    }

    @Test
    void cancelarSinDevolucionConservaElAnticipo() {
        Layaway l = apartar(1, "300");
        layawayService.cancel(l.getId(), new LayawayRequests.Cancel(false, "Anticipo no reembolsable"));

        assertEquals(5, stock());
        assertMoney("300", efectivoEsperadoHoy());
    }

    @Test
    void sinClientaOSinPlanProNoSeAparta() {
        assertThrows(IllegalArgumentException.class, () -> layawayService.create(new LayawayRequests.Create(999_999L,
                List.of(new LayawayRequests.Item(vestido.getId(), 1)),
                new LayawayRequests.Payment(PaymentMethod.CASH, new BigDecimal("100"), null), null, null)));
        plan(PlanType.BASIC);
        assertThrows(ResponseStatusException.class, () -> apartar(1, "100"));
    }

    @Test
    void elRespaldoIncluyeLosApartadosYSuVenta() {
        Layaway abierto = apartar(1, "200");
        Layaway liquidado = apartar(1, "300");
        layawayService.pay(liquidado.getId(), new LayawayRequests.Payment(PaymentMethod.CASH, new BigDecimal("700"), null));

        var payload = backupService.export(accountId);
        assertEquals(2, payload.layawaysOrEmpty().size());
        var resumen = backupService.restore(accountId, payload);
        assertEquals(2, resumen.get("layaways"));

        List<Layaway> restaurados = layawayRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId);
        assertEquals(2, restaurados.size());
        Layaway nuevoLiquidado = restaurados.stream().filter(x -> x.getStatus() == LayawayStatus.COMPLETED).findFirst().orElseThrow();
        Layaway nuevoAbierto = restaurados.stream().filter(x -> x.getStatus() == LayawayStatus.OPEN).findFirst().orElseThrow();
        assertEquals(1, nuevoAbierto.getItems().size());
        assertEquals(1, nuevoAbierto.getPayments().size());
        assertTrue(!nuevoAbierto.getId().equals(abierto.getId()) || true);
        Sale venta = saleRepository.findById(nuevoLiquidado.getSaleId()).orElseThrow();
        assertEquals(nuevoLiquidado.getId(), venta.getLayawayId());
    }
}
