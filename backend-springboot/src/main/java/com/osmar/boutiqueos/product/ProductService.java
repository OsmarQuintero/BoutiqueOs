package com.osmar.boutiqueos.product;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> list(String query) {
        if (query == null || query.isBlank()) {
            return productRepository.findAll();
        }
        return productRepository.findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(query, query);
    }

    public Product get(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    public Product create(ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        return productRepository.save(product);
    }

    public Product update(Long id, ProductRequest request) {
        Product product = get(id);
        apply(product, request);
        return productRepository.save(product);
    }

    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setCategory(request.category());
        product.setSize(request.size());
        product.setColor(request.color());
        product.setSku(request.sku());
        product.setImageUrl(request.imageUrl());
        product.setCostPrice(defaultMoney(request.costPrice()));
        product.setSalePrice(defaultMoney(request.salePrice()));
        product.setStock(request.stock() == null ? 0 : request.stock());
        product.setStatus(request.status() == null ? ProductStatus.ACTIVE : request.status());
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
