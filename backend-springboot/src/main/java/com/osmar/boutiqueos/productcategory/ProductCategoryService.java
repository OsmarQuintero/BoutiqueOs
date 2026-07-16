package com.osmar.boutiqueos.productcategory;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.product.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AccountContext accountContext;

    public ProductCategoryService(ProductCategoryRepository categoryRepository, ProductRepository productRepository, AccountContext accountContext) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.accountContext = accountContext;
    }

    public List<ProductCategory> list() {
        return categoryRepository.findAllByAccountIdOrderByNameAsc(accountContext.requireAccountId());
    }

    public ProductCategory create(ProductCategoryRequest request) {
        validateUniqueName(request.name(), null);
        ProductCategory category = new ProductCategory();
        category.setAccountId(accountContext.requireAccountId());
        apply(category, request);
        return categoryRepository.save(category);
    }

    public ProductCategory update(Long id, ProductCategoryRequest request) {
        ProductCategory category = get(id);
        validateUniqueName(request.name(), id);
        apply(category, request);
        return categoryRepository.save(category);
    }

    public void delete(Long id) {
        ProductCategory category = get(id);
        if (productRepository.countByAccountIdAndCategoryIgnoreCase(accountContext.requireAccountId(), category.getName()) > 0) {
            throw new IllegalStateException("Category is already used by products");
        }
        categoryRepository.delete(category);
    }

    private ProductCategory get(Long id) {
        return categoryRepository.findByIdAndAccountId(id, accountContext.requireAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
    }

    private void apply(ProductCategory category, ProductCategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(blankToNull(request.description()));
        category.setSizeLabel(blankToDefault(request.sizeLabel(), "Talla"));
        category.setActive(request.active() == null || request.active());
    }

    private void validateUniqueName(String rawName, Long currentId) {
        String name = rawName == null ? "" : rawName.trim();
        categoryRepository.findByAccountIdAndNameIgnoreCase(accountContext.requireAccountId(), name).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new IllegalArgumentException("Category already exists: " + name);
            }
        });
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
