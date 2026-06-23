package com.osmar.boutiqueos.settings;

import java.time.Instant;

public record AppSettingsResponse(
        String storeName,
        String phone,
        String address,
        String street,
        String neighborhood,
        String city,
        String postalCode,
        String logoUrl,
        String thankYouMessage,
        String username,
        Instant updatedAt
) {
    public static AppSettingsResponse from(AppSettings settings) {
        return new AppSettingsResponse(
                settings.getStoreName(),
                settings.getPhone(),
                settings.getAddress(),
                settings.getStreet(),
                settings.getNeighborhood(),
                settings.getCity(),
                settings.getPostalCode(),
                settings.getLogoUrl(),
                settings.getThankYouMessage(),
                settings.getUsername(),
                settings.getUpdatedAt()
        );
    }
}
