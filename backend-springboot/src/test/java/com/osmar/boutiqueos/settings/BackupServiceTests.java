package com.osmar.boutiqueos.settings;

import com.osmar.boutiqueos.customer.Customer;
import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.purchase.Purchase;
import com.osmar.boutiqueos.purchase.PurchaseRepository;
import com.osmar.boutiqueos.sale.PaymentMethod;
import com.osmar.boutiqueos.sale.Sale;
import com.osmar.boutiqueos.sale.SaleItem;
import com.osmar.boutiqueos.sale.SaleRepository;
import com.osmar.boutiqueos.sale.SaleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BackupServiceTests {

    /**
     * La cuenta se crea en cada prueba y su id lo asigna la secuencia: los ids
     * no se pueden forzar, asi que se toma el que salga.
     */
    private Long ACCOUNT;

    @Autowired private BackupService backupService;
    @Autowired private TransactionTemplate tx;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private PurchaseRepository purchaseRepository;
    @Autowired private AppSettingsRepository appSettingsRepository;
    @Autowired private com.osmar.boutiqueos.promotion.PromotionRepository promotionRepository;

    @BeforeEach
    void prepararCuenta() {
        AppSettings settings = new AppSettings();
        settings.setStoreName("Boutique de prueba");
        // username es unico y por defecto vale "admin": sin esto, la segunda
        // prueba choca contra la fila que dejo la primera.
        settings.setUsername("respaldo-" + java.util.UUID.randomUUID());
        ACCOUNT = appSettingsRepository.save(settings).getId();
    }

    /**
     * Simula la perdida de datos. Va en su propia transaccion porque los delete
     * derivados de Spring Data la exigen, igual que en produccion, donde quien
     * los llama es un servicio transaccional.
     */
    private void perderTodo() {
        tx.executeWithoutResult(status -> {
            saleRepository.deleteAllByAccountId(ACCOUNT);
            purchaseRepository.deleteAllByAccountId(ACCOUNT);
            productRepository.deleteAllByAccountId(ACCOUNT);
            customerRepository.deleteAllByAccountId(ACCOUNT);
        });
    }

    @Test
    void elRespaldoYaNoRecortaLasComprasA30() {
        Product producto = guardarProducto("Blusa", new BigDecimal("120"), new BigDecimal("250"));
        for (int i = 0; i < 35; i++) {
            guardarCompra(producto, i);
        }

        BackupPayload backup = backupService.export(ACCOUNT);

        // Antes esto exportaba findTop30 y se comia 5 compras sin avisar.
        assertEquals(35, backup.purchasesOrEmpty().size());
    }

    @Test
    void restaurarDejaLaCuentaComoEstabaEnElArchivo() {
        Customer cliente = guardarCliente("Ana");
        Product producto = guardarProducto("Vestido", new BigDecimal("300"), new BigDecimal("700"));
        Instant cuando = Instant.now().minus(9, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        guardarVenta(producto, cliente, cuando);

        BackupPayload backup = backupService.export(ACCOUNT);

        // Se pierde todo, como en un disco muerto.
        perderTodo();
        assertEquals(0, productRepository.findAllByAccountId(ACCOUNT).size());

        Map<String, Integer> conteos = backupService.restore(ACCOUNT, backup);

        assertEquals(1, conteos.get("products"));
        assertEquals(1, conteos.get("customers"));
        assertEquals(1, conteos.get("sales"));

        List<Sale> ventas = saleRepository.findAllByAccountIdOrderByCreatedAtDesc(ACCOUNT);
        assertEquals(1, ventas.size());
        Sale restaurada = ventas.getFirst();

        // La fecha original se conserva: si se aplastara a "hoy", todo el
        // historial de cortes diarios quedaria inservible.
        assertEquals(cuando, restaurada.getCreatedAt());
        assertEquals(0, new BigDecimal("700").compareTo(restaurada.getTotal()));

        // El costo por linea sobrevive: es lo que sostiene el calculo de utilidad.
        SaleItem linea = restaurada.getItems().getFirst();
        assertEquals(0, new BigDecimal("300").compareTo(linea.getUnitCost()));

        // Y las referencias apuntan a las filas nuevas, no a los ids viejos.
        Product productoRestaurado = productRepository.findAllByAccountId(ACCOUNT).getFirst();
        Customer clienteRestaurado = customerRepository.findAllByAccountId(ACCOUNT).getFirst();
        assertNotEquals(producto.getId(), productoRestaurado.getId());
        assertEquals(productoRestaurado.getId(), linea.getProductId());
        assertEquals(clienteRestaurado.getId(), restaurada.getCustomerId());
    }

    @Test
    void restaurarReemplazaNoAcumula() {
        guardarProducto("Falda", new BigDecimal("80"), new BigDecimal("190"));
        BackupPayload backup = backupService.export(ACCOUNT);

        // Restaurar dos veces sobre una cuenta que ya tiene datos no debe duplicar.
        backupService.restore(ACCOUNT, backup);
        backupService.restore(ACCOUNT, backup);

        assertEquals(1, productRepository.findAllByAccountId(ACCOUNT).size());
    }

    @Test
    void rechazaUnArchivoQueNoEsRespaldo() {
        BackupPayload vacio = new BackupPayload(
                Instant.now(), 3, null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThrows(Exception.class, () -> backupService.restore(ACCOUNT, vacio));
        assertThrows(Exception.class, () -> backupService.restore(ACCOUNT, null));
    }

    @Test
    void noSeLlevaDatosDeOtraCuenta() {
        Long otra = 9999L;
        Product ajeno = new Product();
        ajeno.setAccountId(otra);
        ajeno.setName("De otra tienda");
        ajeno.setCostPrice(BigDecimal.ONE);
        ajeno.setSalePrice(BigDecimal.TEN);
        productRepository.save(ajeno);

        guardarProducto("Mio", new BigDecimal("10"), new BigDecimal("20"));

        BackupPayload backup = backupService.export(ACCOUNT);

        assertEquals(1, backup.productsOrEmpty().size());
        assertTrue(backup.productsOrEmpty().stream().allMatch(p -> ACCOUNT.equals(p.getAccountId())));

        tx.executeWithoutResult(status -> productRepository.deleteAllByAccountId(otra));
    }

    @Test
    void laPromocionSobreviveARestaurarYLaVentaSigueLigadaAElla() {
        com.osmar.boutiqueos.promotion.Promotion promo = new com.osmar.boutiqueos.promotion.Promotion();
        promo.setAccountId(ACCOUNT);
        promo.setName("Fin de semana");
        promo.setCode("FINDE");
        promo.setType(com.osmar.boutiqueos.promotion.PromotionType.PERCENT);
        promo.setValue(new BigDecimal("10.00"));
        promo = promotionRepository.save(promo);

        Product producto = guardarProducto("Blusa", new BigDecimal("100"), new BigDecimal("300"));
        Sale venta = new Sale();
        venta.setAccountId(ACCOUNT);
        venta.setPaymentMethod(PaymentMethod.CASH);
        venta.setStatus(SaleStatus.CONFIRMED);
        venta.setSubtotal(new BigDecimal("300"));
        venta.setDiscount(new BigDecimal("50"));
        venta.setManualDiscount(new BigDecimal("20"));
        venta.setPromotionId(promo.getId());
        venta.setPromotionCode("FINDE");
        venta.setPromotionDiscount(new BigDecimal("30"));
        venta.setTotal(new BigDecimal("250"));
        SaleItem linea = new SaleItem();
        linea.setSale(venta);
        linea.setProductId(producto.getId());
        linea.setProductName(producto.getName());
        linea.setQuantity(1);
        linea.setUnitPrice(new BigDecimal("300"));
        linea.setUnitCost(new BigDecimal("100"));
        linea.setLineTotal(new BigDecimal("300"));
        venta.getItems().add(linea);
        saleRepository.save(venta);

        BackupPayload backup = backupService.export(ACCOUNT);
        assertEquals(1, backup.promotionsOrEmpty().size());

        Map<String, Integer> conteos = backupService.restore(ACCOUNT, backup);
        assertEquals(1, conteos.get("promotions"));

        List<com.osmar.boutiqueos.promotion.Promotion> promos =
                promotionRepository.findAllByAccountIdOrderByCreatedAtDesc(ACCOUNT);
        assertEquals(1, promos.size());
        assertEquals("FINDE", promos.getFirst().getCode());

        Sale restaurada = saleRepository.findAllByAccountIdOrderByCreatedAtDesc(ACCOUNT).getFirst();
        // Apunta a la promocion NUEVA (los ids se regeneran) y conserva el desglose.
        assertEquals(promos.getFirst().getId(), restaurada.getPromotionId());
        assertEquals("FINDE", restaurada.getPromotionCode());
        assertEquals(0, new BigDecimal("20").compareTo(restaurada.getManualDiscount()));
        assertEquals(0, new BigDecimal("30").compareTo(restaurada.getPromotionDiscount()));
    }

    private Product guardarProducto(String nombre, BigDecimal costo, BigDecimal precio) {
        Product p = new Product();
        p.setAccountId(ACCOUNT);
        p.setName(nombre);
        p.setCostPrice(costo);
        p.setSalePrice(precio);
        p.setStock(5);
        return productRepository.save(p);
    }

    private Customer guardarCliente(String nombre) {
        Customer c = new Customer();
        c.setAccountId(ACCOUNT);
        c.setName(nombre);
        c.setPhone("8180000000");
        return customerRepository.save(c);
    }

    private void guardarCompra(Product producto, int i) {
        Purchase compra = new Purchase();
        compra.setAccountId(ACCOUNT);
        compra.setSupplierName("Proveedor " + i);
        compra.setProductId(producto.getId());
        compra.setProductName(producto.getName());
        compra.setQuantity(1);
        compra.setUnitCost(new BigDecimal("120"));
        compra.setTotalCost(new BigDecimal("120"));
        purchaseRepository.save(compra);
    }

    private void guardarVenta(Product producto, Customer cliente, Instant cuando) {
        Sale venta = new Sale();
        venta.setAccountId(ACCOUNT);
        venta.setPaymentMethod(PaymentMethod.CASH);
        venta.setStatus(SaleStatus.CONFIRMED);
        venta.setSubtotal(new BigDecimal("700"));
        venta.setTotal(new BigDecimal("700"));
        venta.setEstimatedProfit(new BigDecimal("400"));
        venta.setCustomerId(cliente.getId());
        venta.setCustomerName(cliente.getName());
        venta.setCreatedAt(cuando);

        SaleItem linea = new SaleItem();
        linea.setSale(venta);
        linea.setProductId(producto.getId());
        linea.setProductName(producto.getName());
        linea.setQuantity(1);
        linea.setUnitPrice(new BigDecimal("700"));
        linea.setUnitCost(new BigDecimal("300"));
        linea.setLineTotal(new BigDecimal("700"));
        venta.getItems().add(linea);

        saleRepository.save(venta);
    }
}
