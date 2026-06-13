package com.osmar.boutiqueos.config;

import com.osmar.boutiqueos.customer.Customer;
import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.productcategory.ProductCategory;
import com.osmar.boutiqueos.productcategory.ProductCategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final ProductCategoryRepository productCategoryRepository;

    public DataSeeder(
            ProductRepository productRepository,
            CustomerRepository customerRepository,
            ProductCategoryRepository productCategoryRepository
    ) {
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.productCategoryRepository = productCategoryRepository;
    }

    @Override
    public void run(String... args) {
        if (productCategoryRepository.count() == 0) {
            seedCategory("Vestidos", "Prendas principales para outfit completo", "Talla");
            seedCategory("Blusas", "Blusas y tops para dama", "Talla");
            seedCategory("Pantalones", "Jeans y pantalones", "Talla");
            seedCategory("Tenis", "Calzado casual y deportivo", "Numero");
            seedCategory("Gorras", "Gorras y cachuchas", "Ajuste");
            seedCategory("Accesorios", "Bolsos, cinturones y joyeria", "Medida");
        }
        if (productRepository.count() == 0) {
            seedProduct("Vestido lino", "Vestidos", "M", "Verde olivo", "SKU-VES-001", "310.00", "680.00", 4);
            seedProduct("Blusa satin", "Blusas", "S", "Marfil", "SKU-BLU-002", "180.00", "420.00", 7);
            seedProduct("Jeans recto", "Pantalones", "28", "Azul", "SKU-JEA-003", "390.00", "760.00", 2);
            seedProduct("Bolso mini", "Accesorios", "Unitalla", "Negro", "SKU-BOL-004", "240.00", "540.00", 5);
        }
        if (customerRepository.count() == 0) {
            seedCustomer("María García", "555-0101", "Cliente habitual");
            seedCustomer("Ana López", "555-0102", null);
            seedCustomer("Sofía Martínez", "555-0103", "VIP");
        }
    }

    private void seedProduct(String name, String category, String size, String color, String sku, String cost, String price, int stock) {
        Product product = new Product();
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

    private void seedCategory(String name, String description, String sizeLabel) {
        ProductCategory category = new ProductCategory();
        category.setName(name);
        category.setDescription(description);
        category.setSizeLabel(sizeLabel);
        productCategoryRepository.save(category);
    }

    private void seedCustomer(String name, String phone, String notes) {
        Customer c = new Customer();
        c.setName(name);
        c.setPhone(phone);
        c.setNotes(notes);
        customerRepository.save(c);
    }
}
