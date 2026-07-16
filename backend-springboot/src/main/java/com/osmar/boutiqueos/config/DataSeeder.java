package com.osmar.boutiqueos.config;

import com.osmar.boutiqueos.customer.Customer;
import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.settings.AppSettings;
import com.osmar.boutiqueos.settings.AppSettingsRepository;
import com.osmar.boutiqueos.productcategory.ProductCategory;
import com.osmar.boutiqueos.productcategory.ProductCategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final AppSettingsRepository appSettingsRepository;

    public DataSeeder(
            ProductRepository productRepository,
            CustomerRepository customerRepository,
            ProductCategoryRepository productCategoryRepository,
            AppSettingsRepository appSettingsRepository
    ) {
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.appSettingsRepository = appSettingsRepository;
    }

    @Override
    public void run(String... args) {
        AppSettings demoAccount = appSettingsRepository.findByUsernameIgnoreCase("demo@boutiqueos.local")
                .orElseGet(() -> {
                    AppSettings settings = new AppSettings();
                    settings.setStoreName("Boutique Demo");
                    settings.setUsername("demo@boutiqueos.local");
                    settings.setPassword("demo");
                    return appSettingsRepository.save(settings);
                });

        Long accountId = demoAccount.getId();
        if (productCategoryRepository.findAllByAccountIdOrderByNameAsc(accountId).isEmpty()) {
            seedCategory(accountId, "Vestidos", "Prendas principales para outfit completo", "Talla");
            seedCategory(accountId, "Blusas", "Blusas y tops para dama", "Talla");
            seedCategory(accountId, "Pantalones", "Jeans y pantalones", "Talla");
            seedCategory(accountId, "Tenis", "Calzado casual y deportivo", "Numero");
            seedCategory(accountId, "Gorras", "Gorras y cachuchas", "Ajuste");
            seedCategory(accountId, "Accesorios", "Bolsos, cinturones y joyeria", "Medida");
        }
        if (productRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).isEmpty()) {
            seedProduct(accountId, "Vestido lino", "Vestidos", "M", "Verde olivo", "SKU-VES-001", "310.00", "680.00", 4);
            seedProduct(accountId, "Blusa satin", "Blusas", "S", "Marfil", "SKU-BLU-002", "180.00", "420.00", 7);
            seedProduct(accountId, "Jeans recto", "Pantalones", "28", "Azul", "SKU-JEA-003", "390.00", "760.00", 2);
            seedProduct(accountId, "Bolso mini", "Accesorios", "Unitalla", "Negro", "SKU-BOL-004", "240.00", "540.00", 5);
        }
        if (customerRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).isEmpty()) {
            seedCustomer(accountId, "María García", "555-0101", "Cliente habitual");
            seedCustomer(accountId, "Ana López", "555-0102", null);
            seedCustomer(accountId, "Sofía Martínez", "555-0103", "VIP");
        }
    }

    private void seedProduct(Long accountId, String name, String category, String size, String color, String sku, String cost, String price, int stock) {
        Product product = new Product();
        product.setAccountId(accountId);
        product.setName(name);
        product.setCategory(category);
        product.setSize(size);
        product.setColor(color);
        product.setSku(sku);
        product.setCostPrice(new BigDecimal(cost));
        product.setSalePrice(new BigDecimal(price));
        product.setStock(stock);
        productRepository.save(product);
    }

    private void seedCategory(Long accountId, String name, String description, String sizeLabel) {
        ProductCategory category = new ProductCategory();
        category.setAccountId(accountId);
        category.setName(name);
        category.setDescription(description);
        category.setSizeLabel(sizeLabel);
        productCategoryRepository.save(category);
    }

    private void seedCustomer(Long accountId, String name, String phone, String notes) {
        Customer c = new Customer();
        c.setAccountId(accountId);
        c.setName(name);
        c.setPhone(phone);
        c.setNotes(notes);
        customerRepository.save(c);
    }
}
