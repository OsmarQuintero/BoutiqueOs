package com.osmar.boutiqueos.subscription;

import jakarta.validation.constraints.NotBlank;
public record CheckoutRequest(
        @NotBlank String plan,
        @NotBlank String priceId
) {}
