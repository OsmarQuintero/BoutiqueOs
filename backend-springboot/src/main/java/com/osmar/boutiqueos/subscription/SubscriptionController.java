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
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionService subscriptionService;
    private final StripePrices stripePrices;
    private final String stripeWebhookSecret;

    public SubscriptionController(
            SubscriptionService subscriptionService,
            StripePrices stripePrices,
            @Value("${app.stripe.webhook-secret:}") String stripeWebhookSecret
    ) {
        this.subscriptionService = subscriptionService;
        this.stripePrices = stripePrices;
        this.stripeWebhookSecret = stripeWebhookSecret == null ? "" : stripeWebhookSecret.trim();
    }

    @GetMapping
    public SubscriptionResponse getCurrent() {
        return subscriptionService.getCurrentSubscription();
    }

    @GetMapping("/features")
    public List<Map<String, Object>> getFeatures() {
        return subscriptionService.getAvailableFeatures();
    }

    /**
     * Planes para mostrar en el sistema. Los importes son solo para pintar: lo que
     * se cobra de verdad es el precio de Stripe (anual = 10 meses, 2 gratis).
     */
    @GetMapping("/plans")
    public List<Map<String, Object>> getPlans() {
        return List.of(
                plan(PlanType.BASIC, "Boutique OS Básico", "$499 MXN/mes", "$4,990 MXN/año", "$416 MXN/mes", List.of(
                        "Productos, clientes y ventas ilimitados",
                        "Punto de venta con escáner y pago mixto",
                        "Tallas, colores y etiquetas con código de barras",
                        "Apartados, devoluciones y corte de caja diario",
                        "Importa tu inventario desde Excel",
                        "Alertas de stock bajo y respaldo completo"
                )),
                plan(PlanType.PRO, "Boutique OS Pro", "$999 MXN/mes", "$9,990 MXN/año", "$833 MXN/mes", List.of(
                        "Todo lo del plan Básico",
                        "Hasta 3 usuarios de caja con permisos",
                        "Reportes por periodo con utilidad",
                        "Promociones y puntos de lealtad",
                        "Historial de compras de tus clientas",
                        "Compras a proveedores",
                        "Ticket con tu logo y tus textos"
                ))
        );
    }

    private Map<String, Object> plan(PlanType type, String name, String monthly, String annual,
                                     String annualPerMonth, List<String> features) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("plan", type.name());
        plan.put("name", name);
        plan.put("price", monthly);
        plan.put("priceId", stripePrices.priceFor(type, BillingInterval.MONTHLY));
        plan.put("annualPrice", annual);
        plan.put("annualPerMonth", annualPerMonth);
        plan.put("annualPriceId", stripePrices.priceFor(type, BillingInterval.ANNUAL));
        plan.put("annualSavings", "2 meses gratis");
        plan.put("features", new ArrayList<>(features));
        return plan;
    }

    @PostMapping("/checkout")
    public Map<String, String> createCheckout(@Valid @RequestBody CheckoutRequest request) {
        PlanType plan;
        try {
            plan = PlanType.valueOf(request.plan() == null ? "" : request.plan().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid plan: " + request.plan());
        }
        String url = subscriptionService.createCheckoutSession(plan, BillingInterval.parse(request.interval()));
        return Map.of("checkoutUrl", url);
    }

    /** Portal de pagos de Stripe: cambiar de plan o periodo, tarjeta, facturas y cancelar. */
    @PostMapping("/portal")
    public Map<String, String> portal() {
        return Map.of("url", subscriptionService.createPortalSession());
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
        // Sin firma no se procesa: antes, un POST sin el encabezado se aceptaba sin
        // verificar y cualquiera podia activar planes o cambiar suscripciones.
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Stripe webhook without signature - rejecting");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            String payload = verifyWebhookSignature(body, signatureHeader);
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
        try {
            String timestamp = null;
            List<String> signatures = new ArrayList<>();
            for (String pair : signatureHeader.split(",")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    if ("t".equals(kv[0].trim())) {
                        timestamp = kv[1].trim();
                    } else if ("v1".equals(kv[0].trim())) {
                        // Stripe manda varias firmas v1 mientras se rota el secreto.
                        signatures.add(kv[1].trim());
                    }
                }
            }

            if (timestamp == null || signatures.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature format");
            }

            long eventTime = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - eventTime) > 300) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook timestamp too old");
            }

            String signedPayload = timestamp + "." + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stripeWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = bytesToHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)))
                    .getBytes(StandardCharsets.UTF_8);
            for (String signature : signatures) {
                if (MessageDigest.isEqual(computed, signature.getBytes(StandardCharsets.UTF_8))) {
                    return body;
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook signature mismatch");
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
