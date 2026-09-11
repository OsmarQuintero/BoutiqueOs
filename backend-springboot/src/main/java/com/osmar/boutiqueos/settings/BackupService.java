package com.osmar.boutiqueos.settings;

import com.osmar.boutiqueos.customer.Customer;
import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.customer.loyalty.LoyaltyReward;
import com.osmar.boutiqueos.customer.loyalty.LoyaltyRewardRepository;
import com.osmar.boutiqueos.customer.loyalty.LoyaltyTransaction;
import com.osmar.boutiqueos.customer.loyalty.LoyaltyTransactionRepository;
import com.osmar.boutiqueos.inventory.InventoryMovement;
import com.osmar.boutiqueos.inventory.InventoryMovementRepository;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.productcategory.ProductCategory;
import com.osmar.boutiqueos.productcategory.ProductCategoryRepository;
import com.osmar.boutiqueos.promotion.Promotion;
import com.osmar.boutiqueos.promotion.PromotionRepository;
import com.osmar.boutiqueos.purchase.Purchase;
import com.osmar.boutiqueos.purchase.PurchaseRepository;
import com.osmar.boutiqueos.report.DailyCashCount;
import com.osmar.boutiqueos.report.DailyCashCountRepository;
import com.osmar.boutiqueos.sale.Sale;
import com.osmar.boutiqueos.sale.SaleItem;
import com.osmar.boutiqueos.sale.SaleRefund;
import com.osmar.boutiqueos.sale.SaleRefundItem;
import com.osmar.boutiqueos.sale.SaleRefundRepository;
import com.osmar.boutiqueos.sale.SaleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exporta y restaura el contenido completo de una cuenta.
 *
 * <p>Antes esto vivia en el controlador y solo exportaba. Tenia dos problemas
 * serios: recortaba en silencio (las ultimas 30 compras y 50 movimientos, sin
 * avisar en ningun lado) y no habia forma de volver a cargar el archivo. O sea,
 * se llamaba respaldo pero no lo era.
 *
 * <p>Restaurar <strong>reemplaza</strong>: borra lo que hay en la cuenta y deja
 * exactamente lo que trae el archivo. No mezcla, a proposito. Mezclar duplicaria
 * las ventas y descuadraria el inventario, que es peor que no restaurar.
 */
@Service
public class BackupService {

    private final AppSettingsService appSettingsService;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final SaleRefundRepository saleRefundRepository;
    private final PurchaseRepository purchaseRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final DailyCashCountRepository dailyCashCountRepository;
    private final LoyaltyRewardRepository loyaltyRewardRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final PromotionRepository promotionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public BackupService(
            AppSettingsService appSettingsService,
            ProductRepository productRepository,
            ProductCategoryRepository productCategoryRepository,
            CustomerRepository customerRepository,
            SaleRepository saleRepository,
            SaleRefundRepository saleRefundRepository,
            PurchaseRepository purchaseRepository,
            InventoryMovementRepository inventoryMovementRepository,
            DailyCashCountRepository dailyCashCountRepository,
            LoyaltyRewardRepository loyaltyRewardRepository,
            LoyaltyTransactionRepository loyaltyTransactionRepository,
            PromotionRepository promotionRepository
    ) {
        this.appSettingsService = appSettingsService;
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.saleRefundRepository = saleRefundRepository;
        this.purchaseRepository = purchaseRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.dailyCashCountRepository = dailyCashCountRepository;
        this.loyaltyRewardRepository = loyaltyRewardRepository;
        this.loyaltyTransactionRepository = loyaltyTransactionRepository;
        this.promotionRepository = promotionRepository;
    }

    @Transactional(readOnly = true)
    public BackupPayload export(Long accountId) {
        return new BackupPayload(
                Instant.now(),
                BackupPayload.CURRENT_FORMAT_VERSION,
                // Por accountId explicito y no por getCurrent(): el id ya viene como
                // parametro, y asi el servicio no depende del ThreadLocal de la peticion.
                AppSettingsResponse.from(appSettingsService.getByAccountId(accountId)),
                productRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId),
                productCategoryRepository.findAllByAccountIdOrderByNameAsc(accountId),
                customerRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId),
                saleRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).stream()
                        .map(BackupPayload.BackupSale::from).toList(),
                saleRefundRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).stream()
                        .map(BackupPayload.BackupRefund::from).toList(),
                // Sin findTop30/findTop50: un respaldo recortado no es un respaldo.
                purchaseRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId),
                inventoryMovementRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId),
                dailyCashCountRepository.findAllByAccountIdOrderByBusinessDateDesc(accountId),
                loyaltyRewardRepository.findByAccountIdOrderByNameAsc(accountId),
                loyaltyTransactionRepository.findAllByAccountId(accountId),
                promotionRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId)
        );
    }

    /**
     * Deja la cuenta exactamente como venia en el archivo.
     *
     * <p>Los ids no se reutilizan porque las secuencias son globales y chocarian
     * con las filas de otras cuentas. En su lugar se reinserta todo con ids
     * nuevos y se traducen las referencias con los mapas viejo -> nuevo.
     *
     * @return cuantos registros quedaron por seccion
     */
    @Transactional
    public Map<String, Integer> restore(Long accountId, BackupPayload payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo de respaldo viene vacio");
        }
        if (payload.productsOrEmpty().isEmpty()
                && payload.customersOrEmpty().isEmpty()
                && payload.salesOrEmpty().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El archivo no parece un respaldo de BoutiqueOS: no trae productos, clientes ni ventas");
        }

        wipeAccount(accountId);

        Map<Long, Long> categoryIds = restoreCategories(accountId, payload);
        Map<Long, Long> customerIds = restoreCustomers(accountId, payload);
        Map<Long, Long> productIds = restoreProducts(accountId, payload);
        Map<Long, Long> promotionIds = restorePromotions(accountId, payload, customerIds);

        Map<Long, Long> saleIds = new HashMap<>();
        Map<Long, Long> saleItemIds = new HashMap<>();
        int sales = restoreSales(accountId, payload, productIds, customerIds, promotionIds, saleIds, saleItemIds);
        int refunds = restoreRefunds(accountId, payload, productIds, saleIds, saleItemIds);
        int purchases = restorePurchases(accountId, payload, productIds);
        int movements = restoreMovements(accountId, payload, productIds, saleIds);
        int cashCounts = restoreCashCounts(accountId, payload);
        Map<Long, Long> rewardIds = new HashMap<>();
        int rewards = restoreRewards(accountId, payload, productIds, rewardIds);
        int loyaltyTransactions = restoreLoyaltyTransactions(accountId, payload, customerIds, saleIds, rewardIds);

        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("productCategories", categoryIds.size());
        summary.put("customers", customerIds.size());
        summary.put("products", productIds.size());
        summary.put("sales", sales);
        summary.put("saleRefunds", refunds);
        summary.put("purchases", purchases);
        summary.put("inventoryMovements", movements);
        summary.put("dailyCashCounts", cashCounts);
        summary.put("loyaltyRewards", rewards);
        summary.put("loyaltyTransactions", loyaltyTransactions);
        summary.put("promotions", promotionIds.size());
        return summary;
    }

    /**
     * Borra en orden hijo -> padre para no dejar referencias colgando.
     * Los items de venta y de devolucion se van en cascada con su padre.
     */
    private void wipeAccount(Long accountId) {
        loyaltyTransactionRepository.deleteAllByAccountId(accountId);
        loyaltyRewardRepository.deleteAllByAccountId(accountId);
        promotionRepository.deleteAllByAccountId(accountId);
        saleRefundRepository.deleteAllByAccountId(accountId);
        saleRepository.deleteAllByAccountId(accountId);
        inventoryMovementRepository.deleteAllByAccountId(accountId);
        purchaseRepository.deleteAllByAccountId(accountId);
        dailyCashCountRepository.deleteAllByAccountId(accountId);
        productRepository.deleteAllByAccountId(accountId);
        productCategoryRepository.deleteAllByAccountId(accountId);
        customerRepository.deleteAllByAccountId(accountId);
        // Hibernate ejecuta las inserciones antes que los borrados al hacer flush.
        // Sin esto, reinsertar una promocion con el mismo codigo chocaba contra la
        // fila vieja (codigo unico por tienda) que aun no se habia borrado.
        entityManager.flush();
    }

    private Map<Long, Long> restoreCategories(Long accountId, BackupPayload payload) {
        Map<Long, Long> map = new HashMap<>();
        for (ProductCategory source : payload.categoriesOrEmpty()) {
            Long oldId = source.getId();
            source.setId(null);
            source.setAccountId(accountId);
            ProductCategory saved = productCategoryRepository.save(source);
            if (oldId != null) {
                map.put(oldId, saved.getId());
            }
        }
        return map;
    }

    private Map<Long, Long> restoreCustomers(Long accountId, BackupPayload payload) {
        Map<Long, Long> map = new HashMap<>();
        for (Customer source : payload.customersOrEmpty()) {
            Long oldId = source.getId();
            source.setId(null);
            source.setAccountId(accountId);
            Customer saved = customerRepository.save(source);
            if (oldId != null) {
                map.put(oldId, saved.getId());
            }
        }
        return map;
    }

    private Map<Long, Long> restoreProducts(Long accountId, BackupPayload payload) {
        Map<Long, Long> map = new HashMap<>();
        for (Product source : payload.productsOrEmpty()) {
            Long oldId = source.getId();
            source.setId(null);
            source.setAccountId(accountId);
            Product saved = productRepository.save(source);
            if (oldId != null) {
                map.put(oldId, saved.getId());
            }
        }
        return map;
    }

    private Map<Long, Long> restorePromotions(Long accountId, BackupPayload payload, Map<Long, Long> customerIds) {
        Map<Long, Long> map = new HashMap<>();
        for (Promotion source : payload.promotionsOrEmpty()) {
            Long oldId = source.getId();
            source.setId(null);
            source.setAccountId(accountId);
            source.setCustomerId(remap(customerIds, source.getCustomerId()));
            Promotion saved = promotionRepository.save(source);
            if (oldId != null) {
                map.put(oldId, saved.getId());
            }
        }
        return map;
    }

    private int restoreSales(
            Long accountId,
            BackupPayload payload,
            Map<Long, Long> productIds,
            Map<Long, Long> customerIds,
            Map<Long, Long> promotionIds,
            Map<Long, Long> saleIds,
            Map<Long, Long> saleItemIds
    ) {
        // De la mas vieja a la mas nueva, para que los ids nuevos queden en el
        // mismo orden cronologico que tenian antes.
        List<BackupPayload.BackupSale> ordered = new ArrayList<>(payload.salesOrEmpty());
        ordered.sort(Comparator.comparing(
                BackupPayload.BackupSale::createdAt,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        for (BackupPayload.BackupSale source : ordered) {
            Sale sale = new Sale();
            sale.setAccountId(accountId);
            sale.setPaymentMethod(source.paymentMethod());
            sale.setStatus(source.status());
            sale.setSubtotal(source.subtotal());
            sale.setDiscount(source.discount());
            sale.setTotal(source.total());
            sale.setCashReceived(source.cashReceived());
            sale.setChangeDue(source.changeDue());
            sale.setEstimatedProfit(source.estimatedProfit());
            sale.setRefundedTotal(source.refundedTotal());
            sale.setRefundedProfit(source.refundedProfit());
            sale.setCustomerId(remap(customerIds, source.customerId()));
            sale.setCustomerName(source.customerName());
            if (source.createdAt() != null) {
                sale.setCreatedAt(source.createdAt());
            }
            sale.setRefundedAt(source.refundedAt());
            // Respaldos v2 no traen el desglose: todo el descuento cuenta como manual.
            sale.setManualDiscount(source.manualDiscount() != null ? source.manualDiscount() : source.discount());
            sale.setPromotionId(remap(promotionIds, source.promotionId()));
            sale.setPromotionCode(source.promotionCode());
            // Las cuentas de caja no van en el respaldo (llevarian hashes de
            // contrasenas en un archivo descargable): se conserva solo el nombre.
            sale.setSoldByName(source.soldByName());
            sale.setPromotionDiscount(source.promotionDiscount() != null ? source.promotionDiscount() : BigDecimal.ZERO);

            List<Long> oldItemIds = new ArrayList<>();
            for (BackupPayload.BackupSaleItem sourceItem : source.itemsOrEmpty()) {
                SaleItem item = new SaleItem();
                item.setSale(sale);
                item.setProductId(remap(productIds, sourceItem.productId()));
                item.setProductName(sourceItem.productName());
                item.setQuantity(sourceItem.quantity());
                item.setRefundedQuantity(sourceItem.refundedQuantity());
                item.setUnitPrice(sourceItem.unitPrice());
                item.setUnitCost(sourceItem.unitCost());
                item.setLineTotal(sourceItem.lineTotal());
                sale.getItems().add(item);
                oldItemIds.add(sourceItem.id());
            }

            Sale saved = saleRepository.save(sale);
            if (source.id() != null) {
                saleIds.put(source.id(), saved.getId());
            }
            // Mismo orden de insercion, asi que las listas casan una a una.
            for (int i = 0; i < oldItemIds.size() && i < saved.getItems().size(); i++) {
                Long oldItemId = oldItemIds.get(i);
                if (oldItemId != null) {
                    saleItemIds.put(oldItemId, saved.getItems().get(i).getId());
                }
            }
        }
        return ordered.size();
    }

    private int restoreRefunds(
            Long accountId,
            BackupPayload payload,
            Map<Long, Long> productIds,
            Map<Long, Long> saleIds,
            Map<Long, Long> saleItemIds
    ) {
        for (BackupPayload.BackupRefund source : payload.refundsOrEmpty()) {
            SaleRefund refund = new SaleRefund();
            refund.setAccountId(accountId);
            refund.setSaleId(remap(saleIds, source.saleId()));
            refund.setPaymentMethod(source.paymentMethod());
            refund.setCustomerName(source.customerName());
            refund.setTotal(source.total());
            refund.setEstimatedProfit(source.estimatedProfit());
            if (source.createdAt() != null) {
                refund.setCreatedAt(source.createdAt());
            }

            for (BackupPayload.BackupRefundItem sourceItem : source.itemsOrEmpty()) {
                SaleRefundItem item = new SaleRefundItem();
                item.setRefund(refund);
                item.setSaleItemId(remap(saleItemIds, sourceItem.saleItemId()));
                item.setProductId(remap(productIds, sourceItem.productId()));
                item.setProductName(sourceItem.productName());
                item.setQuantity(sourceItem.quantity());
                item.setUnitPrice(sourceItem.unitPrice());
                item.setTotal(sourceItem.total());
                item.setEstimatedProfit(sourceItem.estimatedProfit());
                refund.getItems().add(item);
            }
            saleRefundRepository.save(refund);
        }
        return payload.refundsOrEmpty().size();
    }

    private int restorePurchases(Long accountId, BackupPayload payload, Map<Long, Long> productIds) {
        for (Purchase source : payload.purchasesOrEmpty()) {
            source.setId(null);
            source.setAccountId(accountId);
            source.setProductId(remap(productIds, source.getProductId()));
            purchaseRepository.save(source);
        }
        return payload.purchasesOrEmpty().size();
    }

    private int restoreMovements(
            Long accountId,
            BackupPayload payload,
            Map<Long, Long> productIds,
            Map<Long, Long> saleIds
    ) {
        for (InventoryMovement source : payload.movementsOrEmpty()) {
            source.setId(null);
            source.setAccountId(accountId);
            source.setProductId(remap(productIds, source.getProductId()));
            // sourceId apunta a la venta que genero el movimiento, cuando aplica.
            source.setSourceId(remap(saleIds, source.getSourceId()));
            inventoryMovementRepository.save(source);
        }
        return payload.movementsOrEmpty().size();
    }

    private int restoreCashCounts(Long accountId, BackupPayload payload) {
        for (DailyCashCount source : payload.cashCountsOrEmpty()) {
            source.setId(null);
            source.setAccountId(accountId);
            dailyCashCountRepository.save(source);
        }
        return payload.cashCountsOrEmpty().size();
    }

    private int restoreRewards(
            Long accountId,
            BackupPayload payload,
            Map<Long, Long> productIds,
            Map<Long, Long> rewardIds
    ) {
        for (LoyaltyReward source : payload.rewardsOrEmpty()) {
            Long oldId = source.getId();
            source.setId(null);
            source.setAccountId(accountId);
            source.setProductId(remap(productIds, source.getProductId()));
            LoyaltyReward saved = loyaltyRewardRepository.save(source);
            if (oldId != null) {
                rewardIds.put(oldId, saved.getId());
            }
        }
        return payload.rewardsOrEmpty().size();
    }

    private int restoreLoyaltyTransactions(
            Long accountId,
            BackupPayload payload,
            Map<Long, Long> customerIds,
            Map<Long, Long> saleIds,
            Map<Long, Long> rewardIds
    ) {
        for (LoyaltyTransaction source : payload.loyaltyTransactionsOrEmpty()) {
            source.setId(null);
            source.setAccountId(accountId);
            source.setCustomerId(remap(customerIds, source.getCustomerId()));
            source.setSaleId(remap(saleIds, source.getSaleId()));
            source.setRewardId(remap(rewardIds, source.getRewardId()));
            loyaltyTransactionRepository.save(source);
        }
        return payload.loyaltyTransactionsOrEmpty().size();
    }

    /**
     * Traduce un id viejo al nuevo. Si el respaldo apunta a algo que ya no
     * existe, devuelve null en lugar de dejar un id que apunta a otra cuenta.
     */
    private Long remap(Map<Long, Long> map, Long oldId) {
        if (oldId == null) {
            return null;
        }
        return map.get(oldId);
    }
}
