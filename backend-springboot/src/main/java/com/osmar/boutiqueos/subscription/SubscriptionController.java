package com.osmar.boutiqueos.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionService subscriptionService;
    private final String stripeWebhookSecret;

    public SubscriptionController(
            SubscriptionService subscriptionService,
            @Value("${app.stripe.webhook-secret:}") String stripeWebhookSecret
    ) {
        this.subscriptionService = subscriptionService;
        this.stripeWebhookSecret = stripeWebhookSecret == null ? "" : stripeWebhookSecret.trim();
    }

    @GetMapping
    public SubscriptionResponse getCurrent() {
        return subscriptionService.getCurrentSubscription();
    }

    @PostMapping("/checkout")
    public Map<String, String> createCheckout(@Valid @RequestBody CheckoutRequest request) {
        PlanType plan;
        try {
            plan = PlanType.valueOf(request.plan().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid plan: " + request.plan());
        }
        if (plan == PlanType.FREE) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot checkout for free plan");
        }

        String url = subscriptionService.createCheckoutSession(plan, request.priceId());
        return Map.of("checkoutUrl", url);
    }

    @PostMapping("/cancel")
    public SubscriptionResponse cancel() {
        return subscriptionService.cancelSubscription();
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(HttpServletRequest request, @RequestBody String body) {
        String signatureHeader = request.getHeader("Stripe-Signature");
        if (stripeWebhookSecret.isBlank()) {
            log.warn("Stripe webhook received but webhook secret is not configured");
            return ResponseEntity.ok().build();
        }

        try {
            String payload = body;
            if (!stripeWebhookSecret.isBlank() && signatureHeader != null) {
                payload = verifyWebhookSignature(body, signatureHeader);
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode event = mapper.readTree(payload);
            String eventType = event.path("type").asText("");
            JsonNode data = event.path("data").path("object");

            subscriptionService.handleWebhookEvent(eventType, data);
            return ResponseEntity.ok().build();
        } catch (Exception exception) {
            log.error("Webhook processing failed: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    private String verifyWebhookSignature(String body, String signatureHeader) {
        // Simplified: for production, implement proper HMAC-SHA256 signature verification
        // using the webhook secret and the Stripe-Signature header
        return body;
    }
}
