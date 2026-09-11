package com.osmar.boutiqueos.onboarding;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class StripeCheckoutController {

    private final StripeCheckoutCreator stripeCheckoutCreator;

    public StripeCheckoutController(StripeCheckoutCreator stripeCheckoutCreator) {
        this.stripeCheckoutCreator = stripeCheckoutCreator;
    }

    /** Desde la landing: /api/checkout/start?plan=pro&interval=annual */
    @GetMapping("/start")
    public ResponseEntity<Void> start(
            @RequestParam(defaultValue = "BASIC") String plan,
            @RequestParam(defaultValue = "monthly") String interval
    ) {
        String checkoutUrl = stripeCheckoutCreator.createCheckoutUrl(plan, interval);
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, checkoutUrl)
                .build();
    }
}
