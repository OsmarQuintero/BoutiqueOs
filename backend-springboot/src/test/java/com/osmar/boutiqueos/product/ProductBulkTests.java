package com.osmar.boutiqueos.product;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.inventory.InventoryMovementRepository;
import com.osmar.boutiqueos.inventory.InventoryMovementType;
import com.osmar.boutiqueos.productcategory.ProductCategoryRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Variantes con SKU unico, SKU automatico y la importacion desde Excel/CSV. */
@SpringBootTest
class ProductBulkTests {

    private static final Long ACCOUNT = 606L;

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductCategoryRepository categoryRepository;
    @Autowired private InventoryMovementRepository movementRepository;
    @Autowired private AccountSubscriptionRepository subscriptionRepository;
    @Autowired private AccountContext accountContext;

    @BeforeEach
    void setUp() {
        accountContext.setAccountId(ACCOUNT);
        AccountSubscription sub = new AccountSubscription();
        sub.setAccountId(ACCOUNT);
        sub.setPlan(PlanType.BASIC);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(sub);
    }

    @AfterEach
    void cleanup() {
        accountContext.clear();
        movementRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        subscriptionRepository.deleteAll();
    }

    private ProductBulkRequests.Variants blusa(List<ProductBulkRequests.VariantStock> stocks) {
        return new ProductBulkRequests.Variants("Blusa Lino", "Blusas", new BigDecimal("150"), new BigDecimal("399"),
                null, List.of("CH", "M", "G"), List.of("Negro", "Blanco"), stocks, 2, 1, null);
    }

    private static ProductRequest simple(String name, String sku) {
        return new ProductRequest(name, "Vestidos", "M", "Rojo", sku, null, new BigDecimal("200"),
                new BigDecimal("500"), 3, ProductStatus.ACTIVE, null, null);
    }

    @Test
    void variantesUnaPorCombinacionConSkuUnico() {
        List<Product> creadas = productService.createVariants(
                blusa(List.of(new ProductBulkRequests.VariantStock("M", "Negro", 5))));

        assertEquals(6, creadas.size());
        Set<String> skus = new HashSet<>();
        creadas.forEach(p -> skus.add(p.getSku()));
        assertEquals(6, skus.size());
        assertTrue(skus.contains("BLUL-M-NEG"), skus.toString());
        assertTrue(creadas.stream().allMatch(p -> "BLUL".equals(p.getStyleCode())));
        assertEquals(5, creadas.stream().filter(p -> "M".equals(p.getSize()) && "Negro".equals(p.getColor()))
                .findFirst().orElseThrow().getStock());
        assertEquals(2, creadas.stream().filter(p -> "G".equals(p.getSize()) && "Blanco".equals(p.getColor()))
                .findFirst().orElseThrow().getStock());

        // Otra vez el mismo modelo: los SKU no chocan.
        List<Product> otra = productService.createVariants(blusa(null));
        assertTrue(otra.stream().anyMatch(p -> "BLUL-M-NEG-2".equals(p.getSku())));
        assertThrows(ResponseStatusException.class, () -> productService.createVariants(
                new ProductBulkRequests.Variants("Nada", null, null, BigDecimal.ONE, null, List.of(), List.of(),
                        null, 1, null, null)));
    }

    @Test
    void skuAutomaticoYSinRepetidos() {
        Product sinSku = productService.create(simple("Vestido rojo", null));
        assertEquals(String.format("P%06d", sinSku.getId()), sinSku.getSku());

        productService.create(simple("Vestido azul", "VES-AZ"));
        var repetido = assertThrows(ResponseStatusException.class, () -> productService.create(simple("Otro", "ves-az")));
        assertEquals(409, repetido.getStatusCode().value());
    }

    @Test
    void importarCreaActualizaYReportaRenglones() {
        Product existente = productService.create(simple("Vestido midi", "VES-1"));
        var filas = List.of(
                new ProductBulkRequests.ImportRow(2, "Falda plisada", "Faldas", "M", "Negro", null,
                        new BigDecimal("180"), new BigDecimal("450"), 4, 1, null),
                new ProductBulkRequests.ImportRow(3, "Vestido midi", null, null, null, "VES-1",
                        null, new BigDecimal("600"), 7, null, null),
                new ProductBulkRequests.ImportRow(4, "  ", null, null, null, null,
                        null, new BigDecimal("100"), 1, null, null));

        var resultado = productService.importProducts(new ProductBulkRequests.ImportRequest(filas, true));

        assertEquals(1, resultado.created());
        assertEquals(1, resultado.updated());
        assertEquals(1, resultado.errors().size());
        assertEquals(4, resultado.errors().get(0).line());
        Product actualizado = productRepository.findById(existente.getId()).orElseThrow();
        assertEquals(7, actualizado.getStock());
        assertEquals(0, new BigDecimal("600").compareTo(actualizado.getSalePrice()));
        assertTrue(movementRepository.findAllByAccountIdOrderByCreatedAtDesc(ACCOUNT).stream()
                .anyMatch(m -> m.getType() == InventoryMovementType.ADJUSTMENT && m.getQuantity() == 4));
        assertTrue(categoryRepository.findByAccountIdAndNameIgnoreCase(ACCOUNT, "faldas").isPresent());

        var sinActualizar = productService.importProducts(new ProductBulkRequests.ImportRequest(List.of(
                new ProductBulkRequests.ImportRow(2, "Vestido midi", null, null, null, "VES-1",
                        null, new BigDecimal("650"), 9, null, null)), false));
        assertEquals(1, sinActualizar.skipped());
        assertEquals(7, productRepository.findById(existente.getId()).orElseThrow().getStock());
    }
}
