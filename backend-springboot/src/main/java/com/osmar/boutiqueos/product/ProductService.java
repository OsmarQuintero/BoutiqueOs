package com.osmar.boutiqueos.product;

import com.osmar.boutiqueos.config.AccountContext;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ProductService {
    private static final Pattern SAFE_DATA_IMAGE = Pattern.compile(
            "^data:image/(png|jpeg|jpg|webp|gif);base64,[a-zA-Z0-9+/=\\r\\n]+$"
    );

    private final ProductRepository productRepository;
    private final AccountContext accountContext;

    public ProductService(ProductRepository productRepository, AccountContext accountContext) {
        this.productRepository = productRepository;
        this.accountContext = accountContext;
    }

    public List<Product> list(String query) {
        Long accountId = accountContext.requireAccountId();
        if (query == null || query.isBlank()) {
            return productRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId);
        }
        return productRepository.findByAccountIdAndNameContainingIgnoreCaseOrAccountIdAndCategoryContainingIgnoreCase(accountId, query, accountId, query);
    }

    public Product get(Long id) {
        return productRepository.findByIdAndAccountId(id, accountContext.requireAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    public Product create(ProductRequest request) {
        Product product = new Product();
        product.setAccountId(accountContext.requireAccountId());
        apply(product, request);
        return productRepository.save(product);
    }

    public Product update(Long id, ProductRequest request) {
        Product product = get(id);
        apply(product, request);
        return productRepository.save(product);
    }

    public void delete(Long id) {
        productRepository.deleteByIdAndAccountId(id, accountContext.requireAccountId());
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(clean(request.name()));
        product.setCategory(clean(request.category()));
        product.setSize(clean(request.size()));
        product.setColor(clean(request.color()));
        product.setSku(clean(request.sku()));
        product.setImageUrl(normalizeImageUrl(request.imageUrl()));
        product.setCostPrice(defaultMoney(request.costPrice()));
        product.setSalePrice(defaultMoney(request.salePrice()));
        product.setStock(request.stock() == null ? 0 : request.stock());
        product.setStatus(request.status() == null ? ProductStatus.ACTIVE : request.status());
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeImageUrl(String value) {
        String clean = clean(value);
        if (clean == null || clean.isBlank()) {
            return null;
        }

        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:text/html") || lower.startsWith("data:image/svg")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image format");
        }

        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return clean;
        }

        if (SAFE_DATA_IMAGE.matcher(clean).matches()) {
            return clean;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image format");
    }
}
