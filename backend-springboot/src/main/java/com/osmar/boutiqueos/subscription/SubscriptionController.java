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
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionService subscriptionService;
    private final String stripeWebhookSecret;
    private final String priceBasic;
    private final String pricePro;

    public SubscriptionController(
            SubscriptionService subscriptionService,
            @Value("${app.stripe.webhook-secret:}") String stripeWebhookSecret,
            @Value("${app.stripe.price-basic:}") String priceBasic,
            @Value("${app.stripe.price-pro:}") String pricePro
    ) {
        this.subscriptionService = subscriptionService;
        this.stripeWebhookSecret = stripeWebhookSecret == null ? "" : stripeWebhookSecret.trim();
        this.priceBasic = priceBasic == null ? "" : priceBasic.trim();
        this.pricePro = pricePro == null ? "" : pricePro.trim();
    }

    @GetMapping
    public SubscriptionResponse getCurrent() {
        return subscriptionService.getCurrentSubscription();
    }

    @GetMapping("/features")
    public List<Map<String, Object>> getFeatures() {
        return subscriptionService.getAvailableFeatures();
    }

    @GetMapping("/plans")
    public List<Map<String, Object>> getPlans() {
        return List.of(
            Map.of(
                "plan", "BASIC",
                "name", "Boutique OS Básico",
                "price", "$500 MXN/mes",
                "priceId", priceBasic,
                "features", List.of(
                    "Productos, clientes y ventas ilimitados",
                    "Categorías e inventario básico",
                    "Punto de venta completo"
                )
            ),
            Map.of(
                "plan", "PRO",
                "name", "Boutique OS Pro",
                "price", "$1,000 MXN/mes",
                "priceId", pricePro,
                "features", List.of(
                    "Todo lo del plan Básico",
                    "Ticket personalizado con tu marca y logo",
                    "Reportes avanzados y corte de caja",
                    "Historial y CRM de clientes",
                    "Promociones y descuentos",
                    "Compras a proveedores",
                    "Devoluciones con trazabilidad",
                    "Multi-usuario",
                    "Respaldo de datos (Excel, CSV, PDF)",
                    "Soporte prioritario"
                )
            )
        );
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

        String priceId = resolvePriceId(plan, request.priceId());
        String url = subscriptionService.createCheckoutSession(plan, priceId);
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
            log.warn("Stripe webhook received but webhook secret is not configured - rejecting");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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

    private String resolvePriceId(PlanType plan, String requestedPriceId) {
        if (requestedPriceId != null && !requestedPriceId.isBlank()) {
            return requestedPriceId;
        }
        return switch (plan) {
            case BASIC -> priceBasic;
            case PRO -> pricePro;
            default -> "";
        };
    }

    private String verifyWebhookSignature(String body, String signatureHeader) {
        try {
            String[] pairs = signatureHeader.split(",");
            String timestamp = null;
            String signature = null;
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    if ("t".equals(kv[0].trim())) {
                        timestamp = kv[1].trim();
                    } else if ("v1".equals(kv[0].trim())) {
                        signature = kv[1].trim();
                    }
                }
            }
            if (timestamp == null || signature == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature format");
            }

            long eventTime = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - eventTime) > 300) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook timestamp too old");
            }

            String signedPayload = timestamp + "." + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stripeWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computedHex = bytesToHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));

            if (!computedHex.equals(signature)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook signature mismatch");
            }
            return body;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook signature verification failed");
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
