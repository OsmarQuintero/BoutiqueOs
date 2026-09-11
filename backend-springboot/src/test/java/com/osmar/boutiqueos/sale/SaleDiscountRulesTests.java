package com.osmar.boutiqueos.sale;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.customer.Customer;
import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.product.ProductStatus;
import com.osmar.boutiqueos.promotion.Promotion;
import com.osmar.boutiqueos.promotion.PromotionRepository;
import com.osmar.boutiqueos.promotion.PromotionRequest;
import com.osmar.boutiqueos.promotion.PromotionService;
import com.osmar.boutiqueos.promotion.PromotionType;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El servidor decide el descuento y valida el efectivo. Antes aceptaba el
 * descuento que mandara el navegador (una venta de $500 podia quedar en $0) y
 * cualquier efectivo recibido, aunque no cubriera el total.
 */
@SpringBootTest
class SaleDiscountRulesTests {

    private static final Long ACCOUNT = 202L;

    @Autowired private SaleService saleService;
    @Autowired private PromotionService promotionService;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AccountSubscriptionRepository subscriptionRepository;
    @Autowired private AccountContext accountContext;

    private Product vestido;

    @BeforeEach
    void setUp() {
        accountContext.setAccountId(ACCOUNT);
        AccountSubscription sub = new AccountSubscription();
        sub.setAccountId(ACCOUNT);
        sub.setPlan(PlanType.PRO);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(sub);

        vestido = new Product();
        vestido.setAccountId(ACCOUNT);
        vestido.setName("Vestido reglas");
        vestido.setCategory("Vestidos");
        vestido.setSku("TEST-DESC-" + System.nanoTime());
        vestido.setCostPrice(new BigDecimal("200.00"));
        vestido.setSalePrice(new BigDecimal("500.00"));
        vestido.setStock(10);
        vestido.setStatus(ProductStatus.ACTIVE);
        vestido = productRepository.save(vestido);
    }

    @AfterEach
    void cleanup() {
        accountContext.clear();
        promotionRepository.deleteAll();
        productRepository.deleteAll();
        customerRepository.deleteAll();
        subscriptionRepository.deleteAll();
    }

    private SaleRequest venta(Long promotionId, String manual, String cash) {
        return new SaleRequest(
                PaymentMethod.CASH,
                null,
                cash == null ? null : new BigDecimal(cash),
                null,
                List.of(new SaleRequest.SaleItemRequest(vestido.getId(), 1)),
                promotionId,
                manual == null ? null : new BigDecimal(manual));
    }

    private Promotion promo(String code, PromotionType type, String value, String min,
                            Long customerId, LocalDate starts, LocalDate ends, boolean active) {
        return promotionService.create(new PromotionRequest(
                "Promo " + code, code, type, new BigDecimal(value), new BigDecimal(min),
                customerId, starts, ends, active, null));
    }

    @Test
    void elServidorIgnoraElDescuentoQueMandeElNavegadorYRecalculaLaPromocion() {
        Promotion diez = promo("DIEZ", PromotionType.PERCENT, "10", "0", null, null, null, true);

        // El navegador dice 500 de descuento; el servidor solo reconoce el 10%.
        Sale sale = saleService.create(new SaleRequest(
                PaymentMethod.CASH, new BigDecimal("500.00"), null, null,
                List.of(new SaleRequest.SaleItemRequest(vestido.getId(), 1)),
                diez.getId(), BigDecimal.ZERO));

        assertEquals(0, new BigDecimal("50.00").compareTo(sale.getDiscount()));
        assertEquals(0, new BigDecimal("450.00").compareTo(sale.getTotal()));
        assertEquals("DIEZ", sale.getPromotionCode());
    }

    @Test
    void laPromocionSeCalculaSobreLoQueQuedaDespuesDelDescuentoManual() {
        Promotion diez = promo("DIEZB", PromotionType.PERCENT, "10", "0", null, null, null, true);

        Sale sale = saleService.create(venta(diez.getId(), "100.00", null));

        // (500 - 100) * 10% = 40, igual que lo muestra el POS.
        assertEquals(0, new BigDecimal("100.00").compareTo(sale.getManualDiscount()));
        assertEquals(0, new BigDecimal("40.00").compareTo(sale.getPromotionDiscount()));
        assertEquals(0, new BigDecimal("360.00").compareTo(sale.getTotal()));
    }

    @Test
    void unaPromocionQueYaNoAplicaRechazaLaVentaConLaRazon() {
        LocalDate hoy = LocalDate.now();
        Promotion vencida = promo("VIEJA", PromotionType.FIXED, "50", "0", null, hoy.minusDays(10), hoy.minusDays(1), true);
        Promotion futura = promo("FUTURA", PromotionType.FIXED, "50", "0", null, hoy.plusDays(2), null, true);
        Promotion apagada = promo("APAGADA", PromotionType.FIXED, "50", "0", null, null, null, false);
        Promotion minima = promo("MINIMA", PromotionType.FIXED, "50", "900", null, null, null, true);

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> saleService.create(venta(vencida.getId(), null, null))).getMessage().contains("vencio"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> saleService.create(venta(futura.getId(), null, null))).getMessage().contains("no empieza"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> saleService.create(venta(apagada.getId(), null, null))).getMessage().contains("desactivada"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> saleService.create(venta(minima.getId(), null, null))).getMessage().contains("compra minima"));
    }

    @Test
    void unaPromocionExclusivaSoloAplicaASuClienta() {
        Customer ana = new Customer();
        ana.setAccountId(ACCOUNT);
        ana.setName("Ana");
        ana.setPhone("8180000000");
        ana = customerRepository.save(ana);
        Promotion deAna = promo("ANA", PromotionType.FIXED, "80", "0", ana.getId(), null, null, true);

        assertThrows(IllegalArgumentException.class,
                () -> saleService.create(venta(deAna.getId(), null, null)));

        Sale conAna = saleService.create(new SaleRequest(
                PaymentMethod.CASH, null, null, ana.getId(),
                List.of(new SaleRequest.SaleItemRequest(vestido.getId(), 1)),
                deAna.getId(), null));
        assertEquals(0, new BigDecimal("420.00").compareTo(conAna.getTotal()));
    }

    @Test
    void unaVentaConClientaSeGuardaYLeSumaPuntos() {
        Customer lucia = new Customer();
        lucia.setAccountId(ACCOUNT);
        lucia.setName("Lucia");
        lucia.setPhone("8181111111");
        lucia = customerRepository.save(lucia);

        // Antes, si sumar puntos fallaba, se perdia la venta entera. La venta
        // tiene que guardarse Y los puntos tienen que sumarse: $500 / 20 = 25.
        Sale sale = saleService.create(new SaleRequest(
                PaymentMethod.CASH, null, null, lucia.getId(),
                List.of(new SaleRequest.SaleItemRequest(vestido.getId(), 1)),
                null, null));

        assertEquals(lucia.getId(), sale.getCustomerId());
        assertEquals(25, customerRepository.findById(lucia.getId()).orElseThrow().getLoyaltyPoints());
    }

    @Test
    void elEfectivoQueNoCubreElTotalSeRechaza() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> saleService.create(venta(null, null, "100.00")));
        assertTrue(error.getMessage().contains("no cubre el total"));
    }

    @Test
    void siNoSeCapturaElEfectivoSeTomaComoPagoExacto() {
        Sale sale = saleService.create(venta(null, null, null));
        assertEquals(0, new BigDecimal("500.00").compareTo(sale.getCashReceived()));
        assertEquals(0, BigDecimal.ZERO.compareTo(sale.getChangeDue()));

        Sale conCambio = saleService.create(venta(null, null, "600.00"));
        assertEquals(0, new BigDecimal("100.00").compareTo(conCambio.getChangeDue()));
    }

    @Test
    void lasVentasViejasDeLaColaSinConexionSiguenEntrando() {
        // Formato anterior: solo "discount". Se trata como descuento manual.
        Sale sale = saleService.create(new SaleRequest(
                PaymentMethod.CASH, new BigDecimal("30.00"), new BigDecimal("470.00"), null,
                List.of(new SaleRequest.SaleItemRequest(vestido.getId(), 1))));
        assertEquals(0, new BigDecimal("470.00").compareTo(sale.getTotal()));
        assertEquals(0, new BigDecimal("30.00").compareTo(sale.getManualDiscount()));
    }

    @Test
    void elDescuentoManualNuncaDejaElTotalNegativo() {
        Sale sale = saleService.create(venta(null, "9999.00", null));
        assertEquals(0, BigDecimal.ZERO.compareTo(sale.getTotal()));
    }

    @Test
    void elCodigoDePromocionNoSeRepiteEnLaMismaTienda() {
        promo("UNICO", PromotionType.FIXED, "10", "0", null, null, null, true);
        assertThrows(IllegalArgumentException.class,
                () -> promo("unico", PromotionType.FIXED, "20", "0", null, null, null, true));
    }
}
