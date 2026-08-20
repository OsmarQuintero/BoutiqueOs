package com.osmar.boutiqueos.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class StripeCheckoutCreator {

    private static final Logger log = LoggerFactory.getLogger(StripeCheckoutCreator.class);

    private final String stripeSecretKey;
    private final String priceBasic;
    private final String pricePro;
    private final String checkoutMode;
    private final String frontendUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StripeCheckoutCreator(
            @Value("${app.stripe.secret-key:}") String stripeSecretKey,
            @Value("${app.stripe.price-id:}") String priceId,
            @Value("${app.stripe.price-basic:}") String priceBasic,
            @Value("${app.stripe.price-pro:}") String pricePro,
            @Value("${app.stripe.checkout.mode:subscription}") String checkoutMode,
            @Value("${app.frontend.url:http://localhost:4200}") String frontendUrl
    ) {
        this.stripeSecretKey = stripeSecretKey == null ? "" : stripeSecretKey.trim();
        this.priceBasic = (priceBasic != null && !priceBasic.isBlank()) ? priceBasic.trim() : (priceId == null ? "" : priceId.trim());
        this.pricePro = pricePro == null ? "" : pricePro.trim();
        this.checkoutMode = checkoutMode == null ? "subscription" : checkoutMode.trim();
        this.frontendUrl = frontendUrl == null ? "http://localhost:4200" : frontendUrl.trim().replaceAll("/+$", "");
    }

    public String createCheckoutUrl(String plan) {
        if (stripeSecretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured");
        }

        String upperPlan = plan == null ? "BASIC" : plan.trim().toUpperCase();
        String resolvedPriceId;
        switch (upperPlan) {
            case "PRO" -> {
                resolvedPriceId = pricePro;
                if (resolvedPriceId.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe price for PRO is not configured");
                }
            }
            default -> {
                upperPlan = "BASIC";
                resolvedPriceId = priceBasic;
                if (resolvedPriceId.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe price for BASIC is not configured");
                }
            }
        }

        try {
            String successUrl = frontendUrl + "/?session_id={CHECKOUT_SESSION_ID}";
            String cancelUrl = frontendUrl + "/?checkout=cancelled";

            List<String> form = new ArrayList<>();
            form.add("mode=" + encode(checkoutMode));
            form.add("success_url=" + encode(successUrl));
            form.add("cancel_url=" + encode(cancelUrl));
            form.add("line_items[0][price]=" + encode(resolvedPriceId));
            form.add("line_items[0][quantity]=1");
            form.add("allow_promotion_codes=true");
            form.add("metadata[plan]=" + encode(upperPlan));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/checkout/sessions"))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((stripeSecretKey + ":").getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(String.join("&", form)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                String detail = describeStripeError(response);
                log.error("Stripe checkout session creation failed: status={} detail={}", response.statusCode(), detail);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, detail);
            }

            JsonNode payload = objectMapper.readTree(response.body());
            String url = payload.path("url").asText("");
            if (url.isBlank()) {
                log.error("Stripe checkout response missing url: {}", response.body());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe checkout URL is missing");
            }
            return url;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Stripe checkout session failed", exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe checkout session failed");
        }
    }

    private String describeStripeError(HttpResponse<String> response) {
        try {
            JsonNode error = objectMapper.readTree(response.body()).path("error");
            String type = error.path("type").asText("");
            String code = error.path("code").asText("");
            String message = error.path("message").asText("");
            if (!message.isBlank()) {
                return "Stripe " + response.statusCode() + " (" + type + " / " + code + "): " + message;
            }
        } catch (Exception ignored) {
            // fall through to generic message
        }
        return "Stripe returned " + response.statusCode();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
