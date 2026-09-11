package com.osmar.boutiqueos.settings.staff;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Modelos de /api/staff. */
public final class StaffRequests {

    private StaffRequests() {
    }

    public record Create(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(min = 12, max = 72) String password,
            @Min(0) @Max(100) Integer maxDiscountPercent
    ) {
    }

    public record Update(
            @NotBlank @Size(max = 120) String name,
            Boolean active,
            @Min(0) @Max(100) Integer maxDiscountPercent
    ) {
    }

    public record NewPassword(@NotBlank @Size(min = 12, max = 72) String password) {
    }

    public record View(
            Long id,
            String name,
            String username,
            boolean active,
            int maxDiscountPercent,
            Instant createdAt,
            Instant lastLoginAt
    ) {
        public static View from(StaffUser user) {
            return new View(user.getId(), user.getName(), user.getUsername(), user.isActive(),
                    user.getMaxDiscountPercent(), user.getCreatedAt(), user.getLastLoginAt());
        }
    }
}
