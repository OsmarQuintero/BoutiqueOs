package com.osmar.boutiqueos.sale;

import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public SaleService(SaleRepository saleRepository, ProductRepository productRepository, CustomerRepository customerRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
    }

    public List<Sale> listToday() {
        var zone = ZoneId.systemDefault();
        var start = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        var end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        return saleRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }

    @Transactional
    public Sale create(SaleRequest request) {
        Sale sale = new Sale();
        sale.setPaymentMethod(request.paymentMethod());
        sale.setDiscount(request.discount() == null ? BigDecimal.ZERO : request.discount());
        sale.setStatus(request.paymentMethod() == PaymentMethod.CASH ? SaleStatus.CONFIRMED : SaleStatus.PENDING);

        if (request.customerId() != null) {
            var customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.customerId()));
            sale.setCustomerId(customer.getId());
            sale.setCustomerName(customer.getName());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal estimatedProfit = BigDecimal.ZERO;

        for (SaleRequest.SaleItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemRequest.productId()));
            if (product.getStock() < itemRequest.quantity()) {
                throw new IllegalArgumentException("Not enough stock for " + product.getName());
            }

            product.setStock(product.getStock() - itemRequest.quantity());

            BigDecimal quantity = BigDecimal.valueOf(itemRequest.quantity());
            BigDecimal lineTotal = product.getSalePrice().multiply(quantity);
            BigDecimal lineCost = product.getCostPrice().multiply(quantity);

            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(product.getSalePrice());
            item.setUnitCost(product.getCostPrice());
            item.setLineTotal(lineTotal);
            sale.getItems().add(item);

            subtotal = subtotal.add(lineTotal);
            estimatedProfit = estimatedProfit.add(lineTotal.subtract(lineCost));
        }

        BigDecimal total = subtotal.subtract(sale.getDiscount()).max(BigDecimal.ZERO);
        sale.setSubtotal(subtotal);
        sale.setTotal(total);
        sale.setEstimatedProfit(estimatedProfit.subtract(sale.getDiscount()).max(BigDecimal.ZERO));

        return saleRepository.save(sale);
    }

    public List<Sale> listPending() {
        return saleRepository.findByStatus(SaleStatus.PENDING);
    }

    @Transactional
    public Sale confirm(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + id));
        sale.setStatus(SaleStatus.CONFIRMED);
        return saleRepository.save(sale);
    }
}
