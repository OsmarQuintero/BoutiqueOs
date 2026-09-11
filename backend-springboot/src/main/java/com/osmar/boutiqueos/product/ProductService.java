package com.osmar.boutiqueos.product;

import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.text.Normalizer;
import com.osmar.boutiqueos.productcategory.ProductCategoryRepository;
import com.osmar.boutiqueos.productcategory.ProductCategory;
import com.osmar.boutiqueos.inventory.InventoryService;
import com.osmar.boutiqueos.inventory.InventoryMovementType;
import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.subscription.SubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private static final Pattern SAFE_DATA_IMAGE = Pattern.compile(
            "^data:image/(png|jpeg|jpg|webp|gif);base64,[a-zA-Z0-9+/=\\r\\n]+$"
    );

    private static final int MAX_VARIANTS = 60;
    private static final int MAX_IMPORT_ROWS = 2000;
    private static final Pattern NOT_SKU_CHAR = Pattern.compile("[^A-Z0-9]");

    private final ProductRepository productRepository;
    private final AccountContext accountContext;
    private final SubscriptionService subscriptionService;
    private final InventoryService inventoryService;
    private final ProductCategoryRepository categoryRepository;

    public ProductService(
            ProductRepository productRepository,
            AccountContext accountContext,
            SubscriptionService subscriptionService,
            InventoryService inventoryService,
            ProductCategoryRepository categoryRepository
    ) {
        this.productRepository = productRepository;
        this.accountContext = accountContext;
        this.subscriptionService = subscriptionService;
        this.inventoryService = inventoryService;
        this.categoryRepository = categoryRepository;
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id));
    }

    @Transactional
    public Product create(ProductRequest request) {
        subscriptionService.checkLimits("product");
        Product product = new Product();
        product.setAccountId(accountContext.requireAccountId());
        apply(product, request);
        requireUniqueSku(product.getAccountId(), product.getSku(), null);
        return withSku(productRepository.save(product));
    }

    @Transactional
    public Product update(Long id, ProductRequest request) {
        Product product = get(id);
        String previousSku = product.getSku();
        apply(product, request);
        // Solo si cambio: SKUs repetidos que ya existian no bloquean editar lo demas.
        if (product.getSku() != null && !product.getSku().equalsIgnoreCase(previousSku == null ? "" : previousSku)) {
            requireUniqueSku(product.getAccountId(), product.getSku(), product.getId());
        }
        return withSku(productRepository.save(product));
    }

    /**
     * Crea un producto por cada combinacion de talla y color, con el mismo modelo
     * y un SKU propio (base-TALLA-COL) listo para la etiqueta y el escaner.
     */
    @Transactional
    public List<Product> createVariants(ProductBulkRequests.Variants request) {
        subscriptionService.checkLimits("product");
        Long accountId = accountContext.requireAccountId();
        List<String> sizes = cleanList(request.sizes());
        List<String> colors = cleanList(request.colors());
        if (sizes.isEmpty() && colors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe al menos una talla o un color");
        }
        List<String> sizeAxis = sizes.isEmpty() ? java.util.Collections.singletonList(null) : sizes;
        List<String> colorAxis = colors.isEmpty() ? java.util.Collections.singletonList(null) : colors;
        if (sizeAxis.size() * colorAxis.size() > MAX_VARIANTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Son demasiadas combinaciones: el maximo es " + MAX_VARIANTS + " por modelo");
        }
        Map<String, Integer> stockByKey = new HashMap<>();
        if (request.stocks() != null) {
            for (ProductBulkRequests.VariantStock variant : request.stocks()) {
                stockByKey.put(variantKey(variant.size(), variant.color()), variant.stock());
            }
        }
        int defaultStock = request.stock() == null ? 0 : request.stock();
        Set<String> taken = takenSkus(accountId);
        String base = skuBase(request.styleCode(), request.name());
        String styleCode = clean(request.styleCode());
        if (styleCode == null || styleCode.isBlank()) {
            styleCode = base;
        }
        String imageUrl = normalizeImageUrl(request.imageUrl());

        List<Product> created = new ArrayList<>();
        for (String size : sizeAxis) {
            for (String color : colorAxis) {
                Product product = new Product();
                product.setAccountId(accountId);
                product.setName(clean(request.name()));
                product.setCategory(clean(request.category()));
                product.setSize(size);
                product.setColor(color);
                product.setStyleCode(styleCode);
                product.setImageUrl(imageUrl);
                product.setCostPrice(defaultMoney(request.costPrice()));
                product.setSalePrice(defaultMoney(request.salePrice()));
                int stock = stockByKey.getOrDefault(variantKey(size, color), defaultStock);
                product.setStock(stock);
                product.setMinStock(request.minStock());
                product.setStatus(stock == 0 ? ProductStatus.OUT_OF_STOCK : ProductStatus.ACTIVE);
                StringBuilder sku = new StringBuilder(base);
                if (size != null) sku.append('-').append(skuToken(size, 4));
                if (color != null) sku.append('-').append(skuToken(color, 3));
                product.setSku(uniqueSku(sku.toString(), taken));
                created.add(productRepository.save(product));
            }
        }
        return created;
    }

    /**
     * Importa productos de una hoja. Con SKU que ya existe: actualiza (si se pidio)
     * y un cambio de stock queda como ajuste de inventario. Los renglones con error
     * se reportan con su numero y los demas se importan.
     */
    @Transactional
    public ProductBulkRequests.ImportResult importProducts(ProductBulkRequests.ImportRequest request) {
        subscriptionService.checkLimits("product");
        Long accountId = accountContext.requireAccountId();
        List<ProductBulkRequests.ImportRow> rows = request.rows() == null ? List.of() : request.rows();
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximo " + MAX_IMPORT_ROWS + " renglones por archivo");
        }
        Map<String, Product> bySku = new HashMap<>();
        for (Product product : productRepository.findAllByAccountId(accountId)) {
            if (product.getSku() != null && !product.getSku().isBlank()) {
                bySku.putIfAbsent(product.getSku().trim().toLowerCase(Locale.ROOT), product);
            }
        }
        Set<String> categories = new HashSet<>();
        categoryRepository.findAllByAccountIdOrderByNameAsc(accountId)
                .forEach(category -> categories.add(category.getName().trim().toLowerCase(Locale.ROOT)));

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<ProductBulkRequests.RowError> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ProductBulkRequests.ImportRow row = rows.get(i);
            int line = row.line() != null ? row.line() : i + 2;
            String name = clean(row.name());
            String problem = null;
            if (name == null || name.isBlank()) {
                problem = "Falta el nombre";
            } else if (name.length() > 200) {
                problem = "El nombre es demasiado largo (maximo 200 letras)";
            } else if (row.salePrice() == null || row.salePrice().signum() < 0) {
                problem = "Falta el precio de venta";
            } else if (row.costPrice() != null && row.costPrice().signum() < 0) {
                problem = "El costo no puede ser negativo";
            } else if (row.stock() != null && row.stock() < 0) {
                problem = "El stock no puede ser negativo";
            }
            if (problem != null) {
                errors.add(new ProductBulkRequests.RowError(line, problem));
                continue;
            }
            String sku = blankToNull(row.sku());
            String category = blankToNull(row.category());
            Product existing = sku == null ? null : bySku.get(sku.toLowerCase(Locale.ROOT));
            if (existing != null) {
                if (!request.updateExisting()) {
                    skipped++;
                    errors.add(new ProductBulkRequests.RowError(line,
                            "El SKU " + sku + " ya es de \"" + existing.getName() + "\"; se omitio"));
                    continue;
                }
                existing.setName(name);
                if (category != null) existing.setCategory(category);
                if (blankToNull(row.size()) != null) existing.setSize(blankToNull(row.size()));
                if (blankToNull(row.color()) != null) existing.setColor(blankToNull(row.color()));
                if (row.costPrice() != null) existing.setCostPrice(row.costPrice());
                existing.setSalePrice(row.salePrice());
                if (row.minStock() != null) existing.setMinStock(row.minStock());
                if (blankToNull(row.styleCode()) != null) existing.setStyleCode(blankToNull(row.styleCode()));
                if (row.stock() != null && row.stock() != existing.getStock()) {
                    int delta = row.stock() - existing.getStock();
                    existing.setStock(row.stock());
                    inventoryService.syncProductStatus(existing);
                    inventoryService.recordMovement(existing, InventoryMovementType.ADJUSTMENT, delta,
                            existing.getCostPrice(), "Importacion de Excel");
                }
                productRepository.save(existing);
                updated++;
            } else {
                Product product = new Product();
                product.setAccountId(accountId);
                product.setName(name);
                product.setCategory(category);
                product.setSize(blankToNull(row.size()));
                product.setColor(blankToNull(row.color()));
                product.setSku(sku);
                product.setStyleCode(blankToNull(row.styleCode()));
                product.setCostPrice(defaultMoney(row.costPrice()));
                product.setSalePrice(row.salePrice());
                int stock = row.stock() == null ? 0 : row.stock();
                product.setStock(stock);
                product.setMinStock(row.minStock());
                product.setStatus(stock == 0 ? ProductStatus.OUT_OF_STOCK : ProductStatus.ACTIVE);
                product = withSku(productRepository.save(product));
                bySku.put(product.getSku().toLowerCase(Locale.ROOT), product);
                created++;
            }
            // Una categoria nueva en la hoja se da de alta para que aparezca en los filtros.
            if (category != null && categories.add(category.toLowerCase(Locale.ROOT))) {
                ProductCategory newCategory = new ProductCategory();
                newCategory.setAccountId(accountId);
                newCategory.setName(category);
                newCategory.setSizeLabel("Talla");
                newCategory.setActive(true);
                categoryRepository.save(newCategory);
            }
        }
        return new ProductBulkRequests.ImportResult(created, updated, skipped, errors);
    }

    /** Sin SKU no hay etiqueta ni escaner: se le pone P + id (P000123). */
    private Product withSku(Product product) {
        if (product.getSku() == null || product.getSku().isBlank()) {
            product.setSku(String.format("P%06d", product.getId()));
            return productRepository.save(product);
        }
        return product;
    }

    private void requireUniqueSku(Long accountId, String sku, Long currentId) {
        if (sku == null || sku.isBlank()) {
            return;
        }
        productRepository.findFirstByAccountIdAndSkuIgnoreCase(accountId, sku.trim())
                .filter(other -> !other.getId().equals(currentId))
                .ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "El SKU " + sku.trim() + " ya lo tiene \"" + other.getName() + "\". Usa otro para que el escaner no se confunda.");
                });
    }

    private Set<String> takenSkus(Long accountId) {
        Set<String> taken = new HashSet<>();
        for (Product product : productRepository.findAllByAccountId(accountId)) {
            if (product.getSku() != null) {
                taken.add(product.getSku().trim().toLowerCase(Locale.ROOT));
            }
        }
        return taken;
    }

    private static String uniqueSku(String candidate, Set<String> taken) {
        String sku = candidate;
        int n = 2;
        while (taken.contains(sku.toLowerCase(Locale.ROOT))) {
            sku = candidate + "-" + n++;
        }
        taken.add(sku.toLowerCase(Locale.ROOT));
        return sku;
    }

    /** Del modelo si lo hay; si no, de las primeras letras del nombre (Blusa Lino -> BLUL). */
    static String skuBase(String styleCode, String name) {
        String fromStyle = skuToken(styleCode, 10);
        if (!fromStyle.isEmpty()) {
            return fromStyle;
        }
        StringBuilder base = new StringBuilder();
        String[] words = name == null ? new String[0] : name.trim().split("\\s+");
        for (int i = 0; i < words.length && base.length() < 8; i++) {
            String token = skuToken(words[i], i == 0 ? 3 : 1);
            base.append(token);
        }
        return base.isEmpty() ? "PROD" : base.toString();
    }

    static String skuToken(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        String token = NOT_SKU_CHAR.matcher(plain).replaceAll("");
        return token.length() > maxLength ? token.substring(0, maxLength) : token;
    }

    private static String variantKey(String size, String color) {
        return (size == null ? "" : size.trim().toLowerCase(Locale.ROOT)) + "|" + (color == null ? "" : color.trim().toLowerCase(Locale.ROOT));
    }

    private static List<String> cleanList(List<String> values) {
        List<String> clean = new ArrayList<>();
        if (values == null) {
            return clean;
        }
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && seen.add(value.trim().toLowerCase(Locale.ROOT))) {
                clean.add(value.trim());
            }
        }
        return clean;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public void delete(Long id) {
        productRepository.delete(get(id));
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
        product.setMinStock(request.minStock());
        product.setStyleCode(blankToNull(request.styleCode()));
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
