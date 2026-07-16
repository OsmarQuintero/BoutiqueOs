package com.osmar.boutiqueos.product;

import com.osmar.boutiqueos.config.AccountContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ProductServiceAccountIsolationTests {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private AccountContext accountContext;

    @AfterEach
    void cleanup() {
        accountContext.clear();
        productRepository.deleteAll();
    }

    @Test
    void listsOnlyProductsForCurrentAccount() {
        productRepository.save(product(101L, "Vestido Cuenta 1"));
        productRepository.save(product(202L, "Vestido Cuenta 2"));

        accountContext.setAccountId(101L);
        assertEquals(1, productService.list(null).size());
        assertEquals("Vestido Cuenta 1", productService.list(null).getFirst().getName());

        accountContext.setAccountId(202L);
        assertEquals(1, productService.list(null).size());
        assertEquals("Vestido Cuenta 2", productService.list(null).getFirst().getName());
    }

    private Product product(Long accountId, String name) {
        Product product = new Product();
        product.setAccountId(accountId);
        product.setName(name);
        product.setCategory("Vestidos");
        product.setSalePrice(new BigDecimal("100.00"));
        product.setCostPrice(new BigDecimal("50.00"));
        product.setStock(3);
        return product;
    }
}
