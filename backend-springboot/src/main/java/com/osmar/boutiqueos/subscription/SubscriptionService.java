package com.osmar.boutiqueos.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.sale.SaleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final AccountSubscriptionRepository subscriptionRepository;
    private final AccountContext accountContext;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final String stripeSecretKey;
    private final String frontendUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubscriptionService(
            AccountSubscriptionRepository subscriptionRepository,
            AccountContext accountContext,
            ProductRepository productRepository,
            CustomerRepository customerRepository,
            SaleRepository saleRepository,
            @Value("${app.stripe.secret-key:}") String stripeSecretKey,
            @Value("${app.frontend.url:http://localhost:4200}") String frontendUrl
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.accountContext = accountContext;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.stripeSecretKey = stripeSecretKey == null ? "" : stripeSecretKey.trim();
        this.frontendUrl = frontendUrl == null ? "http://localhost:4200" : frontendUrl.trim().replaceAll("/+$", "");
    }

    @Transactional
    public AccountSubscription getOrCreateForAccount(Long accountId) {
        return subscriptionRepository.findByAccountId(accountId)
                .orElseGet(() -> {
                    AccountSubscription sub = new AccountSubscription();
                    sub.setAccountId(accountId);
                    sub.setPlan(PlanType.FREE);
                    sub.setStatus(SubscriptionStatus.ACTIVE);
                    return subscriptionRepository.save(sub);
                });
    }

    @Transactional
    public SubscriptionResponse getCurrentSubscription() {
        Long accountId = accountContext.requireAccountId();
        AccountSubscription sub = getOrCreateForAccount(accountId);
        return SubscriptionResponse.from(sub, getUsage(accountId, sub.getPlan()));
    }

    @Transactional
    public SubscriptionUsage getUsage(Long accountId, PlanType plan) {
        long productCount = productRepository.countByAccountId(accountId);
        long customerCount = customerRepository.countByAccountId(accountId);

        Instant monthStart = LocalDate.now().withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        long salesThisMonth = saleRepository.countByAccountIdAndCreatedAtAfter(accountId, monthStart);

        return new SubscriptionUsage(
                (int) productCount,
                plan.getMaxProducts(),
                (int) customerCount,
                plan.getMaxCustomers(),
                (int) salesThisMonth,
                plan.getMaxSalesPerMonth()
        );
    }

    @Transactional
    public void checkLimits(String resourceType) {
        Long accountId = accountContext.requireAccountId();
        AccountSubscription sub = getOrCreateForAccount(accountId);
        PlanType plan = sub.getPlan();
        SubscriptionUsage usage = getUsage(accountId, plan);

        switch (resourceType) {
            case "product" -> {
                if (!plan.isUnlimited(plan.getMaxProducts()) && usage.productCount() >= plan.getMaxProducts()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Has alcanzado el límite de productos de tu plan " + plan.getDisplayName()
                            + ". Actualiza tu plan para agregar más productos.");
                }
            }
            case "customer" -> {
                if (!plan.isUnlimited(plan.getMaxCustomers()) && usage.customerCount() >= plan.getMaxCustomers()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Has alcanzado el límite de clientes de tu plan " + plan.getDisplayName()
                            + ". Actualiza tu plan para agregar más clientes.");
                }
            }
        }
    }

    public String createCheckoutSession(PlanType targetPlan, String priceId) {
        if (stripeSecretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured");
        }
        if (priceId == null || priceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price ID is required");
        }

        Long accountId = accountContext.requireAccountId();

        try {
            String successUrl = frontendUrl + "/?subscription=success&plan=" + targetPlan.name();
            String cancelUrl = frontendUrl + "/?subscription=cancelled";

            List<String> form = new ArrayList<>();
            form.add("mode=" + encode("subscription"));
            form.add("success_url=" + encode(successUrl));
            form.add("cancel_url=" + encode(cancelUrl));
            form.add("line_items[0][price]=" + encode(priceId));
            form.add("line_items[0][quantity]=1");
            form.add("allow_promotion_codes=true");
            form.add("metadata[account_id]=" + encode(String.valueOf(accountId)));
            form.add("metadata[plan]=" + encode(targetPlan.name()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/checkout/sessions"))
                    .header("Authorization", "Basic " + Base64.getEncoder()
                            .encodeToString((stripeSecretKey + ":").getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(String.join("&", form)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                String detail = describeStripeError(response);
                log.error("Stripe subscription checkout failed: status={} detail={}", response.statusCode(), detail);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, detail);
            }

            JsonNode payload = objectMapper.readTree(response.body());
            String url = payload.path("url").asText("");
            if (url.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe checkout URL is missing");
            }

            String sessionId = payload.path("id").asText("");
            String customerId = payload.path("customer").asText("");

            AccountSubscription sub = getOrCreateForAccount(accountId);
            if (customerId != null && !customerId.isBlank()) {
                sub.setStripeCustomerId(customerId);
            }
            sub.setStripePriceId(priceId);
            sub.setStatus(SubscriptionStatus.INCOMPLETE);
            sub.setUpdatedAt(Instant.now());
            subscriptionRepository.save(sub);

            return url;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Stripe subscription checkout failed", exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe checkout failed");
        }
    }

    @Transactional
    public void handleWebhookEvent(String eventType, JsonNode data) {
        log.info("Processing Stripe webhook: {}", eventType);

        switch (eventType) {
            case "checkout.session.completed" -> handleCheckoutCompleted(data);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(data);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(data);
            case "invoice.payment_failed" -> handlePaymentFailed(data);
            default -> log.debug("Ignoring Stripe event: {}", eventType);
        }
    }

    private void handleCheckoutCompleted(JsonNode data) {
        JsonNode session = data;
        String subscriptionId = session.path("subscription").asText("");
        String customerId = session.path("customer").asText("");
        String accountIdStr = session.path("metadata").path("account_id").asText("");
        String planStr = session.path("metadata").path("plan").asText("");

        if (accountIdStr.isBlank()) {
            log.warn("checkout.session.completed without account_id metadata");
            return;
        }

        Long accountId = Long.parseLong(accountIdStr);
        AccountSubscription sub = getOrCreateForAccount(accountId);

        sub.setStripeSubscriptionId(subscriptionId);
        if (customerId != null && !customerId.isBlank()) {
            sub.setStripeCustomerId(customerId);
        }
        if (!planStr.isBlank()) {
            try {
                sub.setPlan(PlanType.valueOf(planStr));
            } catch (IllegalArgumentException ignored) {}
        }
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setUpdatedAt(Instant.now());

        if (!subscriptionId.isBlank()) {
            fetchSubscriptionPeriods(subscriptionId, sub);
        }

        subscriptionRepository.save(sub);
        log.info("Activated subscription for account {}: plan={}", accountId, sub.getPlan());
    }

    private void handleSubscriptionUpdated(JsonNode data) {
        String subscriptionId = data.path("id").asText("");
        if (subscriptionId.isBlank()) return;

        subscriptionRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(sub -> {
            String status = data.path("status").asText("");
            sub.setStatus(mapStripeStatus(status));
            sub.setUpdatedAt(Instant.now());
            fetchSubscriptionPeriods(subscriptionId, sub);
            subscriptionRepository.save(sub);
            log.info("Updated subscription {}: status={}", subscriptionId, status);
        });
    }

    private void handleSubscriptionDeleted(JsonNode data) {
        String subscriptionId = data.path("id").asText("");
        if (subscriptionId.isBlank()) return;

        subscriptionRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            sub.setPlan(PlanType.FREE);
            sub.setUpdatedAt(Instant.now());
            subscriptionRepository.save(sub);
            log.info("Cancelled subscription {}: reverted to FREE", subscriptionId);
        });
    }

    private void handlePaymentFailed(JsonNode data) {
        String subscriptionId = data.path("subscription").asText("");
        if (subscriptionId.isBlank()) return;

        subscriptionRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.PAST_DUE);
            sub.setUpdatedAt(Instant.now());
            subscriptionRepository.save(sub);
            log.warn("Payment failed for subscription {}", subscriptionId);
        });
    }

    private void fetchSubscriptionPeriods(String stripeSubscriptionId, AccountSubscription sub) {
        if (stripeSecretKey.isBlank()) return;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/subscriptions/" + stripeSubscriptionId))
                    .header("Authorization", "Basic " + Base64.getEncoder()
                            .encodeToString((stripeSecretKey + ":").getBytes(StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 400) {
                JsonNode payload = objectMapper.readTree(response.body());
                long periodStart = payload.path("current_period_start").asLong(0);
                long periodEnd = payload.path("current_period_end").asLong(0);
                if (periodStart > 0) sub.setCurrentPeriodStart(Instant.ofEpochSecond(periodStart));
                if (periodEnd > 0) sub.setCurrentPeriodEnd(Instant.ofEpochSecond(periodEnd));
            }
        } catch (Exception exception) {
            log.warn("Failed to fetch subscription periods from Stripe: {}", exception.getMessage());
        }
    }

    @Transactional
    public SubscriptionResponse cancelSubscription() {
        Long accountId = accountContext.requireAccountId();
        AccountSubscription sub = getOrCreateForAccount(accountId);

        if (sub.getStripeSubscriptionId() != null && !sub.getStripeSubscriptionId().isBlank()) {
            cancelStripeSubscription(sub.getStripeSubscriptionId());
        }

        sub.setStatus(SubscriptionStatus.CANCELLED);
        sub.setPlan(PlanType.FREE);
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);

        return SubscriptionResponse.from(sub, getUsage(accountId, sub.getPlan()));
    }

    private void cancelStripeSubscription(String stripeSubscriptionId) {
        if (stripeSecretKey.isBlank()) return;
        try {
            List<String> form = new ArrayList<>();
            form.add("cancel_at_period_end=true");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/subscriptions/" + stripeSubscriptionId))
                    .header("Authorization", "Basic " + Base64.getEncoder()
                            .encodeToString((stripeSecretKey + ":").getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(String.join("&", form)))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception exception) {
            log.warn("Failed to cancel Stripe subscription: {}", exception.getMessage());
        }
    }

    private SubscriptionStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled", "unpaid" -> SubscriptionStatus.CANCELLED;
            case "incomplete" -> SubscriptionStatus.INCOMPLETE;
            case "incomplete_expired" -> SubscriptionStatus.INCOMPLETE_EXPIRED;
            default -> SubscriptionStatus.ACTIVE;
        };
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
        } catch (Exception ignored) {}
        return "Stripe returned " + response.statusCode();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
