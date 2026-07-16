package com.osmar.boutiqueos.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final String stripeSecretKey;
    private final String stripePriceId;
    private final String checkoutMode;
    private final String frontendUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StripeCheckoutCreator(
            @Value("${app.stripe.secret-key:}") String stripeSecretKey,
            @Value("${app.stripe.price-id:}") String stripePriceId,
            @Value("${app.stripe.checkout.mode:subscription}") String checkoutMode,
            @Value("${app.frontend.url:http://localhost:4200}") String frontendUrl
    ) {
        this.stripeSecretKey = stripeSecretKey == null ? "" : stripeSecretKey.trim();
        this.stripePriceId = stripePriceId == null ? "" : stripePriceId.trim();
        this.checkoutMode = checkoutMode == null ? "subscription" : checkoutMode.trim();
        this.frontendUrl = frontendUrl == null ? "http://localhost:4200" : frontendUrl.trim().replaceAll("/+$", "");
    }

    public String createCheckoutUrl() {
        if (stripeSecretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured");
        }
        if (stripePriceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe price is not configured");
        }

        try {
            String successUrl = frontendUrl + "/?session_id={CHECKOUT_SESSION_ID}";
            String cancelUrl = frontendUrl + "/?checkout=cancelled";

            List<String> form = new ArrayList<>();
            form.add("mode=" + encode(checkoutMode));
            form.add("success_url=" + encode(successUrl));
            form.add("cancel_url=" + encode(cancelUrl));
            form.add("line_items[0][price]=" + encode(stripePriceId));
            form.add("line_items[0][quantity]=1");
            form.add("allow_promotion_codes=true");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/checkout/sessions"))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((stripeSecretKey + ":").getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(String.join("&", form)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe checkout session could not be created");
            }

            JsonNode payload = objectMapper.readTree(response.body());
            String url = payload.path("url").asText("");
            if (url.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe checkout URL is missing");
            }
            return url;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe checkout session failed");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
