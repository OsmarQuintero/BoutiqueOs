package com.osmar.boutiqueos.layaway;

import com.osmar.boutiqueos.sale.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Modelos de /api/layaways. */
public final class LayawayRequests {

    private LayawayRequests() {
    }

    public record Create(
            @NotNull Long customerId,
            @NotEmpty List<@Valid Item> items,
            @NotNull @Valid Payment deposit,
            LocalDate dueDate,
            @Size(max = 500) String notes
    ) {
    }

    public record Item(@NotNull Long productId, @Min(1) int quantity) {
    }

    public record Payment(
            @NotNull PaymentMethod method,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Size(max = 200) String note
    ) {
    }

    /** @param refundPayments true = se le devuelve lo abonado en efectivo; false = la tienda lo conserva. */
    public record Cancel(boolean refundPayments, @Size(max = 300) String reason) {
    }
}
