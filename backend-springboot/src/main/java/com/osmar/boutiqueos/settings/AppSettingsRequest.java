package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;

public record AppSettingsRequest(
        @NotBlank String storeName,
        String phone,
        String address,
        String logoUrl,
        String thankYouMessage,
        @NotBlank String username,
        String password
) {
}
