package com.osmar.boutiqueos.onboarding;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class StripeCheckoutController {

    private final StripeCheckoutCreator stripeCheckoutCreator;

    public StripeCheckoutController(StripeCheckoutCreator stripeCheckoutCreator) {
        this.stripeCheckoutCreator = stripeCheckoutCreator;
    }

    @GetMapping("/start")
    public ResponseEntity<Void> start() {
        String checkoutUrl = stripeCheckoutCreator.createCheckoutUrl();
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, checkoutUrl)
                .build();
    }
}
