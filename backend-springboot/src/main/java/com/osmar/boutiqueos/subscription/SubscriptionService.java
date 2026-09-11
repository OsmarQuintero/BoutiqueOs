package com.osmar.boutiqueos.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.onboarding.OnboardingService;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.sale.SaleRepository;
import com.osmar.boutiqueos.settings.AppSettingsRepository;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Suscripciones con Stripe.
 *
 * <p>Reglas para que el negocio cobre lo que da y no deje a nadie sin sus datos:
 * <ul>
 *   <li>Con pago vencido hay {@value #GRACE_DAYS} dias de gracia; despues la cuenta
 *       queda en solo lectura (consulta y respaldo si, vender no) hasta que pague.</li>
 *   <li>Cancelar no quita el acceso de golpe: sigue hasta el fin del periodo pagado.</li>
 *   <li>Quien ya tiene suscripcion cambia de plan o de periodo en el portal de Stripe
 *       (con prorrateo), nunca con un segundo checkout que le cobraria doble.</li>
 *   <li>El precio lo decide el servidor a partir del plan y el periodo.</li>
 * </ul>
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    static final int GRACE_DAYS = 7;

    private final AccountSubscriptionRepository subscriptionRepository;
    private final AccountContext accountContext;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final AppSettingsRepository appSettingsRepository;
    private final OnboardingService onboardingService;
    private final StripePrices stripePrices;
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
            AppSettingsRepository appSettingsRepository,
            @Value("${app.stripe.secret-key:}") String stripeSecretKey,
            @Value("${app.frontend.url:http://localhost:4200}") String frontendUrl,
            OnboardingService onboardingService,
            StripePrices stripePrices
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.accountContext = accountContext;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.appSettingsRepository = appSettingsRepository;
        this.stripeSecretKey = stripeSecretKey == null ? "" : stripeSecretKey.trim();
        this.frontendUrl = frontendUrl == null ? "http://localhost:4200" : frontendUrl.trim().replaceAll("/+$", "");
        this.onboardingService = onboardingService;
        this.stripePrices = stripePrices;
    }

    @Transactional
    public AccountSubscription getOrCreateForAccount(Long accountId) {
        return subscriptionRepository.findByAccountId(accountId)
                .orElseGet(() -> {
                    AccountSubscription sub = new AccountSubscription();
                    sub.setAccountId(accountId);
                    sub.setStatus(SubscriptionStatus.INCOMPLETE);
                    return subscriptionRepository.save(sub);
                });
    }

    @Transactional
    public SubscriptionResponse getCurrentSubscription() {
        Long accountId = accountContext.requireAccountId();
        AccountSubscription sub = getOrCreateForAccount(accountId);
        return response(accountId, sub);
    }

    private SubscriptionResponse response(Long accountId, AccountSubscription sub) {
        boolean blocked = !isDemoAccount(accountId) && standing(sub) == Standing.BLOCKED;
        return SubscriptionResponse.from(sub, getUsage(accountId, sub.getPlan()), graceEndsAt(sub), blocked);
    }

    @Transactional
    public SubscriptionUsage getUsage(Long accountId, PlanType plan) {
        long productCount = productRepository.countByAccountId(accountId);
        long customerCount = customerRepository.countByAccountId(accountId);

        Instant monthStart = LocalDate.now().withDayOfMonth(1)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        long salesThisMonth = saleRepository.countByAccountIdAndCreatedAtAfter(accountId, monthStart);

        return new SubscriptionUsage(
                (int) productCount, -1,
                (int) customerCount, -1,
                (int) salesThisMonth, -1
        );
    }

    @Transactional
    public void checkLimits(String resourceType) {
        Long accountId = accountContext.requireAccountId();
        if (isDemoAccount(accountId)) return;
        AccountSubscription sub = getOrCreateForAccount(accountId);
        PlanType plan = sub.getPlan();

        if (plan == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes una suscripción activa. Elige un plan para continuar.");
        }
    }

    public void requireFeature(String feature) {
        Long accountId = accountContext.requireAccountId();
        if (isDemoAccount(accountId)) return;
        AccountSubscription sub = getOrCreateForAccount(accountId);
        PlanType plan = sub.getPlan();

        if (plan == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes una suscripción activa. Elige un plan para continuar.");
        }

        if (!plan.hasFeature(feature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esta función requiere el plan " + getUpgradePlan(plan).getDisplayName()
                    + ". Actualiza tu plan para acceder.");
        }
    }

    public List<Map<String, Object>> getAvailableFeatures() {
        Long accountId = accountContext.requireAccountId();
        boolean demo = isDemoAccount(accountId);

        String[] allFeatures = {
            "ticket_customization", "reports", "cash_count", "customer_history",
            "backup", "promotions", "multi_user", "purchases", "refunds", "layaways"
        };

        List<Map<String, Object>> result = new ArrayList<>();
        for (String f : allFeatures) {
            result.add(Map.of("feature", f, "enabled", demo || isProFeature(f, accountId)));
        }
        return result;
    }

    /**
     * Descargar el respaldo: con un plan que lo incluya, y tambien con la
     * suscripcion cancelada o vencida. Los datos son de la tienda (y el aviso de
     * privacidad promete acceso a ellos); lo que se corta es vender, no sacar su informacion.
     */
    public void requireDataExport() {
        Long accountId = accountContext.requireAccountId();
        if (isDemoAccount(accountId)) return;
        AccountSubscription sub = getOrCreateForAccount(accountId);
        if (sub.getPlan() != null && sub.getPlan().hasFeature("backup")) return;
        if (sub.getStatus() == SubscriptionStatus.CANCELLED || sub.getStatus() == SubscriptionStatus.UNPAID
                || sub.getStatus() == SubscriptionStatus.PAST_DUE) return;
        requireFeature("backup");
    }

    /** Usuarias de caja activas que permite el plan de la cuenta. */
    public int maxStaffUsers(Long accountId) {
        if (isDemoAccount(accountId)) return 50;
        PlanType plan = getOrCreateForAccount(accountId).getPlan();
        return plan == null ? 0 : plan.getMaxStaffUsers();
    }

    // ------------------------------------------------------------------ acceso

    enum Standing { OK, GRACE, BLOCKED }

    Standing standing(AccountSubscription sub) {
        if (sub.getPlan() == null) {
            return Standing.BLOCKED;
        }
        return switch (sub.getStatus()) {
            // INCOMPLETE con plan: cuentas viejas cuyo checkout de cambio quedo a medias.
            case ACTIVE, TRIALING, INCOMPLETE -> Standing.OK;
            case PAST_DUE -> Instant.now().isBefore(graceEndsAt(sub)) ? Standing.GRACE : Standing.BLOCKED;
            case CANCELLED, UNPAID, INCOMPLETE_EXPIRED -> Standing.BLOCKED;
        };
    }

    private Instant graceEndsAt(AccountSubscription sub) {
        if (sub.getStatus() != SubscriptionStatus.PAST_DUE) {
            return null;
        }
        Instant since = sub.getPastDueSince() != null ? sub.getPastDueSince() : sub.getUpdatedAt();
        return since.plus(Duration.ofDays(GRACE_DAYS));
    }

    /**
     * Por que esta cuenta no puede modificar nada, o null si si puede. Consultar y
     * descargar el respaldo siempre se permite: los datos son de la tienda.
     */
    @Transactional(readOnly = true)
    public String writeBlockReason(Long accountId) {
        if (isDemoAccount(accountId)) return null;
        // Sin registro de suscripcion no se bloquea de golpe: checkLimits ya pide plan.
        AccountSubscription sub = subscriptionRepository.findByAccountId(accountId).orElse(null);
        if (sub == null || standing(sub) != Standing.BLOCKED) {
            return null;
        }
        if (sub.getStatus() == SubscriptionStatus.PAST_DUE) {
            return "Tu pago esta vencido desde hace mas de " + GRACE_DAYS + " dias. Actualiza tu tarjeta en "
                    + "Configuracion > Suscripcion para volver a vender. Mientras tanto puedes consultar y respaldar tu informacion.";
        }
        return "Tu suscripcion no esta activa. Elige un plan en Configuracion > Suscripcion para volver a vender. "
                + "Mientras tanto puedes consultar y respaldar tu informacion.";
    }

    private boolean isDemoAccount(Long accountId) {
        return appSettingsRepository.findById(accountId)
                .map(s -> "admin".equals(s.getRole()))
                .orElse(false);
    }

    private boolean isProFeature(String feature, Long accountId) {
        AccountSubscription sub = getOrCreateForAccount(accountId);
        PlanType plan = sub.getPlan();
        return plan != null && plan.hasFeature(feature);
    }

    private PlanType getUpgradePlan(PlanType current) {
        return switch (current) {
            case BASIC -> PlanType.PRO;
            case PRO -> PlanType.PRO;
        };
    }

    /** Tiene una suscripcion viva en Stripe: los cambios van por el portal. */
    private static boolean hasLiveStripeSubscription(AccountSubscription sub) {
        return sub.getStripeSubscriptionId() != null && !sub.getStripeSubscriptionId().isBlank()
                && sub.getPlan() != null
                && sub.getStatus() != SubscriptionStatus.CANCELLED
                && sub.getStatus() != SubscriptionStatus.INCOMPLETE_EXPIRED;
    }

    // ---------------------------------------------------------------- checkout

    @Transactional
    public String createCheckoutSession(PlanType targetPlan, BillingInterval interval) {
        Long accountId = accountContext.requireAccountId();
        AccountSubscription sub = getOrCreateForAccount(accountId);
        if (hasLiveStripeSubscription(sub)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya tienes una suscripcion. Para cambiar de plan o pasar a pago anual usa \"Administrar suscripcion\".");
        }
        if (stripeSecretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured");
        }
        String priceId = stripePrices.priceFor(targetPlan, interval);
        if (priceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El precio " + targetPlan.name() + " " + interval.name() + " no esta configurado");
        }

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
            form.add("metadata[interval]=" + encode(interval.name()));
            // La suscripcion tambien lleva la cuenta: sirve para los eventos que llegan despues.
            form.add("subscription_data[metadata][account_id]=" + encode(String.valueOf(accountId)));
            // Si ya fue clienta (cancelo y vuelve), se reusa su cliente de Stripe y su historial.
            if (sub.getStripeCustomerId() != null && !sub.getStripeCustomerId().isBlank()) {
                form.add("customer=" + encode(sub.getStripeCustomerId()));
            }

            JsonNode payload = stripePost("/v1/checkout/sessions", form);
            String url = payload.path("url").asText("");
            if (url.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe checkout URL is missing");
            }
            return url;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Stripe subscription checkout failed", exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe checkout failed");
        }
    }

    /**
     * Portal de Stripe: cambiar de plan o a anual (con prorrateo), actualizar la
     * tarjeta, ver facturas y cancelar. Menos codigo propio y menos soporte.
     */
    @Transactional(readOnly = true)
    public String createPortalSession() {
        Long accountId = accountContext.requireAccountId();
        AccountSubscription sub = getOrCreateForAccount(accountId);
        if (sub.getStripeCustomerId() == null || sub.getStripeCustomerId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Todavia no tienes pagos en Stripe. Elige un plan para empezar.");
        }
        if (stripeSecretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured");
        }
        try {
            List<String> form = new ArrayList<>();
            form.add("customer=" + encode(sub.getStripeCustomerId()));
            form.add("return_url=" + encode(frontendUrl + "/?subscription=portal"));
            String url = stripePost("/v1/billing_portal/sessions", form).path("url").asText("");
            if (url.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe portal URL is missing");
            }
            return url;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Stripe billing portal failed", exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe portal failed");
        }
    }

    // ---------------------------------------------------------------- webhooks

    @Transactional
    public void handleWebhookEvent(String eventType, JsonNode data) {
        log.info("Processing Stripe webhook: {}", eventType);

        switch (eventType) {
            case "checkout.session.completed" -> handleCheckoutCompleted(data);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(data);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(data);
            case "invoice.payment_failed" -> handlePaymentFailed(data);
            case "invoice.paid" -> handleInvoicePaid(data);
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
            // Compra desde la landing: todavia no existe la cuenta, por eso el
            // checkout no pudo mandar account_id. Se registra y se le manda al
            // cliente el enlace para activar.
            String checkoutSessionId = session.path("id").asText("");
            if (checkoutSessionId.isBlank()) {
                log.warn("checkout.session.completed sin account_id ni id de sesion; se ignora");
                return;
            }
            onboardingService.registerPaidCheckout(checkoutSessionId);
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
        sub.setBillingInterval(BillingInterval.parse(session.path("metadata").path("interval").asText("")));
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setPastDueSince(null);
        sub.setCancelAtPeriodEnd(false);
        sub.setUpdatedAt(Instant.now());

        // La suscripcion de Stripe es la fuente de verdad: plan y periodo salen del precio cobrado.
        if (!subscriptionId.isBlank()) {
            JsonNode subscription = fetchSubscription(subscriptionId);
            if (subscription != null) {
                applySubscription(subscription, sub);
            }
        }

        subscriptionRepository.save(sub);
        log.info("Activated subscription for account {}: plan={} interval={}", accountId, sub.getPlan(), sub.getBillingInterval());
    }

    private void handleSubscriptionUpdated(JsonNode data) {
        String subscriptionId = data.path("id").asText("");
        if (subscriptionId.isBlank()) return;

        findSubscription(subscriptionId, data).ifPresent(sub -> {
            SubscriptionStatus status = mapStripeStatus(data.path("status").asText(""));
            setStatus(sub, status);
            applySubscription(data, sub);
            sub.setUpdatedAt(Instant.now());
            subscriptionRepository.save(sub);
            log.info("Updated subscription {}: status={} plan={} interval={}", subscriptionId, status, sub.getPlan(), sub.getBillingInterval());
        });
    }

    private void handleSubscriptionDeleted(JsonNode data) {
        String subscriptionId = data.path("id").asText("");
        if (subscriptionId.isBlank()) return;

        findSubscription(subscriptionId, data).ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            sub.setPlan(null);
            sub.setCancelAtPeriodEnd(false);
            sub.setPastDueSince(null);
            sub.setUpdatedAt(Instant.now());
            subscriptionRepository.save(sub);
            log.info("Cancelled subscription {}: reverted to no plan", subscriptionId);
        });
    }

    private void handlePaymentFailed(JsonNode invoice) {
        String subscriptionId = invoiceSubscriptionId(invoice);
        if (subscriptionId.isBlank()) return;

        subscriptionRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(sub -> {
            setStatus(sub, SubscriptionStatus.PAST_DUE);
            sub.setUpdatedAt(Instant.now());
            subscriptionRepository.save(sub);
            log.warn("Payment failed for subscription {}", subscriptionId);
        });
    }

    /** Pago cobrado (renovacion o tarjeta actualizada): vuelve a estar al corriente. */
    private void handleInvoicePaid(JsonNode invoice) {
        String subscriptionId = invoiceSubscriptionId(invoice);
        if (subscriptionId.isBlank()) return;

        subscriptionRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(sub -> {
            if (sub.getStatus() == SubscriptionStatus.PAST_DUE || sub.getStatus() == SubscriptionStatus.UNPAID
                    || sub.getStatus() == SubscriptionStatus.INCOMPLETE) {
                setStatus(sub, SubscriptionStatus.ACTIVE);
            }
            sub.setUpdatedAt(Instant.now());
            subscriptionRepository.save(sub);
            log.info("Invoice paid for subscription {}", subscriptionId);
        });
    }

    /** Antes el id venia en invoice.subscription; en versiones nuevas de la API, en parent.subscription_details. */
    static String invoiceSubscriptionId(JsonNode invoice) {
        String direct = invoice.path("subscription").asText("");
        if (!direct.isBlank()) {
            return direct;
        }
        return invoice.path("parent").path("subscription_details").path("subscription").asText("");
    }

    private java.util.Optional<AccountSubscription> findSubscription(String subscriptionId, JsonNode subscription) {
        var byId = subscriptionRepository.findByStripeSubscriptionId(subscriptionId);
        if (byId.isPresent()) {
            return byId;
        }
        String accountId = subscription.path("metadata").path("account_id").asText("");
        if (accountId.isBlank()) {
            return java.util.Optional.empty();
        }
        return subscriptionRepository.findByAccountId(Long.parseLong(accountId)).map(sub -> {
            sub.setStripeSubscriptionId(subscriptionId);
            return sub;
        });
    }

    private static void setStatus(AccountSubscription sub, SubscriptionStatus status) {
        if (status == SubscriptionStatus.PAST_DUE) {
            if (sub.getPastDueSince() == null) {
                sub.setPastDueSince(Instant.now());
            }
        } else {
            sub.setPastDueSince(null);
        }
        sub.setStatus(status);
    }

    /**
     * Plan, periodo, fechas y cancelacion desde el objeto subscription de Stripe.
     * Desde la API 2025 las fechas del periodo viven en cada item, no en la suscripcion.
     */
    void applySubscription(JsonNode subscription, AccountSubscription sub) {
        JsonNode item = subscription.path("items").path("data").path(0);
        String priceId = item.path("price").path("id").asText("");
        if (!priceId.isBlank()) {
            sub.setStripePriceId(priceId);
            stripePrices.resolve(priceId).ifPresent(match -> {
                sub.setPlan(match.plan());
                sub.setBillingInterval(match.interval());
            });
        }
        long start = firstPositive(subscription.path("current_period_start").asLong(0), item.path("current_period_start").asLong(0));
        long end = firstPositive(subscription.path("current_period_end").asLong(0), item.path("current_period_end").asLong(0));
        if (start > 0) sub.setCurrentPeriodStart(Instant.ofEpochSecond(start));
        if (end > 0) sub.setCurrentPeriodEnd(Instant.ofEpochSecond(end));
        if (subscription.has("cancel_at_period_end") || subscription.has("cancel_at")) {
            boolean cancelling = subscription.path("cancel_at_period_end").asBoolean(false)
                    || subscription.path("cancel_at").asLong(0) > 0;
            sub.setCancelAtPeriodEnd(cancelling);
        }
    }

    private static long firstPositive(long a, long b) {
        return a > 0 ? a : b;
    }

    private JsonNode fetchSubscription(String stripeSubscriptionId) {
        if (stripeSecretKey.isBlank()) return null;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/subscriptions/" + stripeSubscriptionId))
                    .header("Authorization", authHeader())
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 400) {
                return objectMapper.readTree(response.body());
            }
        } catch (Exception exception) {
            log.warn("Failed to fetch subscription from Stripe: {}", exception.getMessage());
        }
        return null;
    }

    // ---------------------------------------------------------------- cancelar

    /**
     * Con suscripcion en Stripe: se cancela al final del periodo pagado y el acceso
     * sigue hasta entonces (el evento customer.subscription.deleted lo cierra).
     */
    @Transactional
    public SubscriptionResponse cancelSubscription() {
        Long accountId = accountContext.requireAccountId();
        AccountSubscription sub = getOrCreateForAccount(accountId);

        if (hasLiveStripeSubscription(sub)) {
            if (!stripeSecretKey.isBlank()) {
                List<String> form = List.of("cancel_at_period_end=true");
                try {
                    stripePost("/v1/subscriptions/" + sub.getStripeSubscriptionId(), form);
                } catch (ResponseStatusException exception) {
                    throw exception;
                } catch (Exception exception) {
                    log.warn("Failed to cancel Stripe subscription: {}", exception.getMessage());
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "No se pudo cancelar en Stripe. Intenta de nuevo o usa \"Administrar suscripcion\".");
                }
            }
            sub.setCancelAtPeriodEnd(true);
        } else {
            // Cuentas sin cobro en Stripe (alta manual): no hay periodo pagado que respetar.
            sub.setStatus(SubscriptionStatus.CANCELLED);
            sub.setPlan(null);
        }
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);
        return response(accountId, sub);
    }

    private SubscriptionStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "unpaid" -> SubscriptionStatus.UNPAID;
            case "canceled" -> SubscriptionStatus.CANCELLED;
            case "incomplete" -> SubscriptionStatus.INCOMPLETE;
            case "incomplete_expired" -> SubscriptionStatus.INCOMPLETE_EXPIRED;
            default -> SubscriptionStatus.ACTIVE;
        };
    }

    private JsonNode stripePost(String path, List<String> form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.stripe.com" + path))
                .header("Authorization", authHeader())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(String.join("&", form)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            String detail = describeStripeError(response);
            log.error("Stripe {} failed: status={} detail={}", path, response.statusCode(), detail);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, detail);
        }
        return objectMapper.readTree(response.body());
    }

    private String authHeader() {
        return "Basic " + Base64.getEncoder().encodeToString((stripeSecretKey + ":").getBytes(StandardCharsets.UTF_8));
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
