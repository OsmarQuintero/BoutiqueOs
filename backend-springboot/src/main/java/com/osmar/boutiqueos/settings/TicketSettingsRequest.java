package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;

public record TicketSettingsRequest(
        @NotBlank String storeName,
        String phone,
        String street,
        String neighborhood,
        String city,
        String postalCode,
        String logoUrl,
        String thankYouMessage
) {
}
