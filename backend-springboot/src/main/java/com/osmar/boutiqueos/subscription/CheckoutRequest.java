package com.osmar.boutiqueos.subscription;

public record CheckoutRequest(
        String plan,
        String priceId
) {}
