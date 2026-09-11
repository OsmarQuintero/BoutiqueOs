package com.osmar.boutiqueos.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.osmar.boutiqueos.customer.Customer;
import com.osmar.boutiqueos.customer.loyalty.LoyaltyReward;
import com.osmar.boutiqueos.customer.loyalty.LoyaltyTransaction;
import com.osmar.boutiqueos.inventory.InventoryMovement;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.productcategory.ProductCategory;
import com.osmar.boutiqueos.promotion.Promotion;
import com.osmar.boutiqueos.purchase.Purchase;
import com.osmar.boutiqueos.report.DailyCashCount;
import com.osmar.boutiqueos.sale.PaymentMethod;
import com.osmar.boutiqueos.sale.Sale;
import com.osmar.boutiqueos.sale.SaleItem;
import com.osmar.boutiqueos.sale.SaleRefund;
import com.osmar.boutiqueos.sale.SaleRefundItem;
import com.osmar.boutiqueos.sale.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * El contenido completo de una cuenta, tal como se exporta y se restaura.
 *
 * <p>Existe porque los DTO de la API no sirven para respaldar: {@code SaleResponse.Item}
 * no lleva {@code unitCost}, asi que un respaldo hecho con el DTO se llevaba las ventas
 * pero no el costo de cada linea, y al restaurar la utilidad de todo el historial
 * quedaba mal. Aqui cada registro va con todos sus campos.
 *
 * <p>Los ids que viajan son los originales. Al restaurar no se reutilizan (las
 * secuencias son globales y chocarian con otras cuentas): sirven solo para volver a
 * amarrar las referencias entre venta, producto y cliente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BackupPayload(
        Instant generatedAt,
        int formatVersion,
        AppSettingsResponse settings,
        List<Product> products,
        List<ProductCategory> productCategories,
        List<Customer> customers,
        List<BackupSale> sales,
        List<BackupRefund> saleRefunds,
        List<Purchase> purchases,
        List<InventoryMovement> inventoryMovements,
        List<DailyCashCount> dailyCashCounts,
        List<LoyaltyReward> loyaltyRewards,
        List<LoyaltyTransaction> loyaltyTransactions,
        List<Promotion> promotions
) {

    /** Version del formato. Se sube cuando cambie la forma del archivo. */
    // v3: agrega promociones y el desglose del descuento de cada venta.
    public static final int CURRENT_FORMAT_VERSION = 3;

    /** Nunca devuelve null, para no tener que checar cada lista al restaurar. */
    public List<Product> productsOrEmpty() { return products == null ? List.of() : products; }
    public List<ProductCategory> categoriesOrEmpty() { return productCategories == null ? List.of() : productCategories; }
    public List<Customer> customersOrEmpty() { return customers == null ? List.of() : customers; }
    public List<BackupSale> salesOrEmpty() { return sales == null ? List.of() : sales; }
    public List<BackupRefund> refundsOrEmpty() { return saleRefunds == null ? List.of() : saleRefunds; }
    public List<Purchase> purchasesOrEmpty() { return purchases == null ? List.of() : purchases; }
    public List<InventoryMovement> movementsOrEmpty() { return inventoryMovements == null ? List.of() : inventoryMovements; }
    public List<DailyCashCount> cashCountsOrEmpty() { return dailyCashCounts == null ? List.of() : dailyCashCounts; }
    public List<LoyaltyReward> rewardsOrEmpty() { return loyaltyRewards == null ? List.of() : loyaltyRewards; }
    public List<LoyaltyTransaction> loyaltyTransactionsOrEmpty() { return loyaltyTransactions == null ? List.of() : loyaltyTransactions; }
    public List<Promotion> promotionsOrEmpty() { return promotions == null ? List.of() : promotions; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BackupSale(
            Long id,
            PaymentMethod paymentMethod,
            SaleStatus status,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal total,
            BigDecimal cashReceived,
            BigDecimal changeDue,
            BigDecimal estimatedProfit,
            BigDecimal refundedTotal,
            BigDecimal refundedProfit,
            Long customerId,
            String customerName,
            Instant createdAt,
            Instant refundedAt,
            List<BackupSaleItem> items,
            BigDecimal manualDiscount,
            Long promotionId,
            String promotionCode,
            BigDecimal promotionDiscount,
            String soldByName
    ) {
        public static BackupSale from(Sale sale) {
            return new BackupSale(
                    sale.getId(),
                    sale.getPaymentMethod(),
                    sale.getStatus(),
                    sale.getSubtotal(),
                    sale.getDiscount(),
                    sale.getTotal(),
                    sale.getCashReceived(),
                    sale.getChangeDue(),
                    sale.getEstimatedProfit(),
                    sale.getRefundedTotal(),
                    sale.getRefundedProfit(),
                    sale.getCustomerId(),
                    sale.getCustomerName(),
                    sale.getCreatedAt(),
                    sale.getRefundedAt(),
                    sale.getItems().stream().map(BackupSaleItem::from).toList(),
                    sale.getManualDiscount(),
                    sale.getPromotionId(),
                    sale.getPromotionCode(),
                    sale.getPromotionDiscount(),
                    sale.getSoldByName()
            );
        }

        public List<BackupSaleItem> itemsOrEmpty() {
            return items == null ? List.of() : items;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BackupSaleItem(
            Long id,
            Long productId,
            String productName,
            int quantity,
            int refundedQuantity,
            BigDecimal unitPrice,
            BigDecimal unitCost,
            BigDecimal lineTotal
    ) {
        public static BackupSaleItem from(SaleItem item) {
            return new BackupSaleItem(
                    item.getId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getRefundedQuantity(),
                    item.getUnitPrice(),
                    item.getUnitCost(),
                    item.getLineTotal()
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BackupRefund(
            Long id,
            Long saleId,
            PaymentMethod paymentMethod,
            String customerName,
            BigDecimal total,
            BigDecimal estimatedProfit,
            Instant createdAt,
            List<BackupRefundItem> items
    ) {
        public static BackupRefund from(SaleRefund refund) {
            return new BackupRefund(
                    refund.getId(),
                    refund.getSaleId(),
                    refund.getPaymentMethod(),
                    refund.getCustomerName(),
                    refund.getTotal(),
                    refund.getEstimatedProfit(),
                    refund.getCreatedAt(),
                    refund.getItems().stream().map(BackupRefundItem::from).toList()
            );
        }

        public List<BackupRefundItem> itemsOrEmpty() {
            return items == null ? List.of() : items;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BackupRefundItem(
            Long id,
            Long saleItemId,
            Long productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal total,
            BigDecimal estimatedProfit
    ) {
        public static BackupRefundItem from(SaleRefundItem item) {
            return new BackupRefundItem(
                    item.getId(),
                    item.getSaleItemId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getTotal(),
                    item.getEstimatedProfit()
            );
        }
    }

    /** Lista mutable, para poder ordenar antes de reinsertar. */
    static <T> List<T> mutable(List<T> source) {
        return new ArrayList<>(source);
    }
}
