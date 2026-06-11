package com.osmar.boutiqueos.sale;

import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.product.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class SaleServiceRefundTests {

    @Autowired
    private SaleService saleService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void refundingConfirmedSaleRestoresStockAndMarksSaleAsRefunded() {
        Product product = new Product();
        product.setName("Vestido prueba devolucion");
        product.setCategory("Vestidos");
        product.setSize("M");
        product.setColor("Negro");
        product.setSku("TEST-REFUND-001");
        product.setCostPrice(new BigDecimal("100.00"));
        product.setSalePrice(new BigDecimal("180.00"));
        product.setStock(3);
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);

        Sale sale = saleService.create(new SaleRequest(
                PaymentMethod.CASH,
                BigDecimal.ZERO,
                null,
                List.of(new SaleRequest.SaleItemRequest(product.getId(), 2))
        ));

        Product afterSale = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(1, afterSale.getStock());

        Sale refunded = saleService.refund(sale.getId(), null);
        Product afterRefund = productRepository.findById(product.getId()).orElseThrow();

        assertEquals(SaleStatus.REFUNDED, refunded.getStatus());
        assertNotNull(refunded.getRefundedAt());
        assertEquals(3, afterRefund.getStock());
        assertEquals(ProductStatus.ACTIVE, afterRefund.getStatus());
    }

    @Test
    void partialRefundRestoresOnlyRequestedUnitsAndKeepsSaleOpenForMoreRefunds() {
        Product product = new Product();
        product.setName("Blusa prueba parcial");
        product.setCategory("Blusas");
        product.setSize("S");
        product.setColor("Blanco");
        product.setSku("TEST-REFUND-002");
        product.setCostPrice(new BigDecimal("80.00"));
        product.setSalePrice(new BigDecimal("200.00"));
        product.setStock(5);
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);

        Sale sale = saleService.create(new SaleRequest(
                PaymentMethod.CASH,
                new BigDecimal("30.00"),
                null,
                List.of(new SaleRequest.SaleItemRequest(product.getId(), 3))
        ));

        SaleItem saleItem = sale.getItems().getFirst();
        Sale partial = saleService.refund(sale.getId(), new SaleRefundRequest(
                List.of(new SaleRefundRequest.Item(saleItem.getId(), 1))
        ));

        Product afterPartialRefund = productRepository.findById(product.getId()).orElseThrow();

        assertEquals(SaleStatus.PARTIALLY_REFUNDED, partial.getStatus());
        assertEquals(3, afterPartialRefund.getStock());
        assertEquals(1, partial.getItems().getFirst().getRefundedQuantity());
        assertEquals(new BigDecimal("190.00"), partial.getRefundedTotal());
    }
}
