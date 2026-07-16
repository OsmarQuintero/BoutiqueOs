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
import java.util.Base64;

@Service
public class StripeCheckoutVerifier {

    private final String stripeSecretKey;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StripeCheckoutVerifier(@Value("${app.stripe.secret-key:}") String stripeSecretKey) {
        this.stripeSecretKey = stripeSecretKey == null ? "" : stripeSecretKey.trim();
    }

    public StripeCheckoutDetails verifyPaidSession(String sessionId) {
        if (stripeSecretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured");
        }

        try {
            String encodedSessionId = URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/checkout/sessions/" + encodedSessionId))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((stripeSecretKey + ":").getBytes(StandardCharsets.UTF_8)))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe session could not be verified");
            }

            JsonNode payload = objectMapper.readTree(response.body());
            String status = payload.path("status").asText("");
            String paymentStatus = payload.path("payment_status").asText("");
            if (!"complete".equalsIgnoreCase(status) || !"paid".equalsIgnoreCase(paymentStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe payment is not complete");
            }

            String customerEmail = payload.path("customer_details").path("email").asText("");
            if (customerEmail.isBlank()) {
                customerEmail = payload.path("customer_email").asText("");
            }

            return new StripeCheckoutDetails(
                    payload.path("id").asText(sessionId),
                    customerEmail.isBlank() ? null : customerEmail.trim()
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe verification failed");
        }
    }

    public record StripeCheckoutDetails(String sessionId, String customerEmail) {
    }
}
