package com.osmar.boutiqueos.sale;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Lo que necesita SaleService para registrar un apartado liquidado como venta,
 * sin conocer la entidad del apartado.
 *
 * @param payments lo abonado por metodo (sin devoluciones)
 */
public record LayawaySale(
        Long layawayId,
        Long customerId,
        String customerName,
        String soldByName,
        List<Line> lines,
        Map<PaymentMethod, BigDecimal> payments
) {
    public record Line(Long productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal unitCost) {
    }
}
