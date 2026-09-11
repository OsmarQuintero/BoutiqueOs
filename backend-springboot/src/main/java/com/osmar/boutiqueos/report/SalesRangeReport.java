package com.osmar.boutiqueos.report;

import com.osmar.boutiqueos.sale.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reporte de ventas de un periodo (dias de la tienda, ambos incluidos).
 *
 * <p>Las ventas cuentan el dia en que se cobraron y las devoluciones el dia en
 * que se devolvio el dinero, igual que el corte diario. Las pendientes de pago
 * no suman: se informan aparte.
 */
public record SalesRangeReport(
        LocalDate from,
        LocalDate to,
        Totals totals,
        List<DayRow> days,
        List<MethodRow> paymentMethods,
        List<ProductRow> products,
        List<CategoryRow> categories,
        List<SellerRow> sellers
) {

    public record Totals(
            int salesCount,
            int units,
            BigDecimal grossSales,
            BigDecimal discounts,
            BigDecimal salesTotal,
            BigDecimal refundsTotal,
            BigDecimal netSales,
            BigDecimal profit,
            BigDecimal averageTicket,
            BigDecimal marginPercent,
            int pendingCount,
            BigDecimal pendingTotal
    ) {
    }

    public record DayRow(LocalDate date, int salesCount, BigDecimal salesTotal, BigDecimal refundsTotal, BigDecimal netSales) {
    }

    public record MethodRow(PaymentMethod method, BigDecimal salesTotal, BigDecimal refundsTotal, BigDecimal net) {
    }

    public record ProductRow(Long productId, String productName, String category, int units, BigDecimal revenue, BigDecimal profit) {
    }

    public record CategoryRow(String category, int units, BigDecimal revenue, BigDecimal profit, BigDecimal sharePercent) {
    }

    public record SellerRow(String seller, int salesCount, BigDecimal salesTotal) {
    }
}
