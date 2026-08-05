package com.osmar.boutiqueos.productcategory;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductCategoryServiceTests {

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCategoryService categoryService;

    @Autowired
    private AccountContext accountContext;

    @AfterEach
    void cleanup() {
        accountContext.clear();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void deletesAnUnusedCategory() {
        ProductCategory category = categoryRepository.save(category(101L, "Vestidos"));

        accountContext.setAccountId(101L);
        categoryService.delete(category.getId());

        assertFalse(categoryRepository.existsById(category.getId()));
    }

    @Test
    void rejectsDeletingACategoryUsedByAProduct() {
        ProductCategory category = categoryRepository.save(category(101L, "Vestidos"));
        productRepository.save(product(101L, "Vestidos"));

        accountContext.setAccountId(101L);
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.delete(category.getId())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    private ProductCategory category(Long accountId, String name) {
        ProductCategory category = new ProductCategory();
        category.setAccountId(accountId);
        category.setName(name);
        return category;
    }

    private Product product(Long accountId, String category) {
        Product product = new Product();
        product.setAccountId(accountId);
        product.setName("Vestido prueba");
        product.setCategory(category);
        product.setCostPrice(new BigDecimal("100.00"));
        product.setSalePrice(new BigDecimal("200.00"));
        product.setStock(1);
        return product;
    }
}
