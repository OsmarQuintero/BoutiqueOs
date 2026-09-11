package com.osmar.boutiqueos.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Alta de variantes (tallas x colores) e importacion de productos desde Excel/CSV. */
public final class ProductBulkRequests {

    private ProductBulkRequests() {
    }

    /**
     * Un modelo con varias tallas y/o colores: se crea un producto por combinacion,
     * todos con el mismo {@code styleCode} y un SKU propio para escanear.
     */
    public record Variants(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 100) String category,
            @DecimalMin("0.00") BigDecimal costPrice,
            @NotNull @DecimalMin("0.00") BigDecimal salePrice,
            @Size(max = 60) String styleCode,
            @Size(max = 30) List<@Size(max = 50) String> sizes,
            @Size(max = 30) List<@Size(max = 50) String> colors,
            List<@Valid VariantStock> stocks,
            @Min(0) Integer stock,
            @Min(0) @Max(9999) Integer minStock,
            @Size(max = 10_000) String imageUrl
    ) {
    }

    public record VariantStock(String size, String color, @Min(0) int stock) {
    }

    /** @param updateExisting si el SKU ya existe: true = actualiza, false = lo omite */
    public record ImportRequest(@NotNull @Size(max = 2000) List<ImportRow> rows, boolean updateExisting) {
    }

    /** @param line renglon de la hoja (para que el error diga donde) */
    public record ImportRow(
            Integer line,
            String name,
            String category,
            String size,
            String color,
            String sku,
            BigDecimal costPrice,
            BigDecimal salePrice,
            Integer stock,
            Integer minStock,
            String styleCode
    ) {
    }

    public record ImportResult(int created, int updated, int skipped, List<RowError> errors) {
    }

    public record RowError(int line, String message) {
    }
}
