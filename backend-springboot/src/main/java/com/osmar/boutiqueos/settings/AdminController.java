package com.osmar.boutiqueos.settings;

import com.osmar.boutiqueos.subscription.AccountSubscription;
import com.osmar.boutiqueos.subscription.AccountSubscriptionRepository;
import com.osmar.boutiqueos.subscription.PlanType;
import com.osmar.boutiqueos.subscription.SubscriptionStatus;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final String ADMIN_SECRET_HEADER = "X-Admin-Secret";

    private final AppSettingsRepository appSettingsRepository;
    private final AppSettingsService appSettingsService;
    private final AccountSubscriptionRepository subscriptionRepository;
    private final String adminSecret;

    public AdminController(
            AppSettingsRepository appSettingsRepository,
            AppSettingsService appSettingsService,
            AccountSubscriptionRepository subscriptionRepository,
            @Value("${app.admin.secret:}") String adminSecret
    ) {
        this.appSettingsRepository = appSettingsRepository;
        this.appSettingsService = appSettingsService;
        this.subscriptionRepository = subscriptionRepository;
        this.adminSecret = adminSecret == null ? "" : adminSecret.trim();
    }

    @PostMapping("/demo-account")
    public Map<String, String> createDemoAccount(
            @RequestHeader(value = ADMIN_SECRET_HEADER, required = false) String providedSecret
    ) {
        if (adminSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin secret is not configured");
        }
        if (providedSecret == null || !java.security.MessageDigest.isEqual(
                providedSecret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                adminSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin secret");
        }

        String username = "demo";
        String password = "demo1234";

        AppSettings existing = appSettingsRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (existing != null) {
            existing.setPassword(hashPasswordDirect(password));
            existing.setRole("admin");
            existing.setUpdatedAt(Instant.now());
            existing = appSettingsRepository.save(existing);

            ensureDemoSubscription(existing.getId());

            return Map.of(
                    "username", username,
                    "password", password,
                    "accountId", String.valueOf(existing.getId()),
                    "role", "admin",
                    "plan", "PRO",
                    "action", "reset"
            );
        }

        AppSettings settings = new AppSettings();
        settings.setStoreName("Boutique Demo");
        settings.setUsername(username);
        settings.setPassword(hashPasswordDirect(password));
        settings.setRole("admin");
        Instant now = Instant.now();
        settings.setUpdatedAt(now);
        settings.setRegistrationCompletedAt(now);
        settings = appSettingsRepository.save(settings);

        ensureDemoSubscription(settings.getId());

        return Map.of(
                "username", username,
                "password", password,
                "accountId", String.valueOf(settings.getId()),
                "role", "admin",
                "plan", "PRO",
                "action", "created"
        );
    }

    /**
     * Crea o restablece la cuenta de dueño con la contraseña que se le mande.
     *
     * <p>Es el reemplazo de las credenciales fijas admin/admin: da el mismo
     * acceso total, pero solo lo puede usar quien tiene el secreto de
     * administracion, y la contraseña no la conoce nadie mas.
     */
    @PostMapping("/owner-account")
    public Map<String, String> provisionOwner(
            @RequestHeader(value = ADMIN_SECRET_HEADER, required = false) String providedSecret,
            @Valid @RequestBody OwnerAccountRequest request
    ) {
        requireAdmin(providedSecret);

        AppSettings settings = appSettingsService.provisionOwner(
                request.username(), request.password(), request.storeName());

        ensureOwnerSubscription(settings.getId());

        return Map.of(
                "username", settings.getUsername(),
                "accountId", String.valueOf(settings.getId()),
                "role", "admin",
                "plan", PlanType.PRO.name()
        );
    }

    private void ensureOwnerSubscription(Long accountId) {
        AccountSubscription sub = subscriptionRepository.findByAccountId(accountId)
                .orElseGet(() -> {
                    AccountSubscription fresh = new AccountSubscription();
                    fresh.setAccountId(accountId);
                    return fresh;
                });
        sub.setPlan(PlanType.PRO);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);
    }

    /**
     * Repara cuentas que pagaron pero quedaron sin plan asignado.
     *
     * <p>Un checkout sin {@code metadata.plan} dejaba {@code plan = null}, y con
     * el plan en null el sistema responde "No tienes una suscripcion activa" y
     * bloquea todo, aunque Stripe le siga cobrando al cliente cada mes. Esto
     * arregla los registros que ya quedaron asi; la causa esta corregida en
     * {@code PlanResolver}.
     *
     * <p>Devuelve el detalle de lo que cambio para poder revisarlo antes y
     * despues. Es idempotente: correrlo dos veces no hace nada la segunda.
     */
    @PostMapping("/subscriptions/repair")
    public Map<String, Object> repairSubscriptionsWithoutPlan(
            @RequestHeader(value = ADMIN_SECRET_HEADER, required = false) String providedSecret,
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        requireAdmin(providedSecret);

        List<AccountSubscription> broken = subscriptionRepository.findAll().stream()
                .filter(sub -> sub.getPlan() == null)
                .toList();

        List<Map<String, String>> detail = broken.stream()
                .map(sub -> Map.of(
                        "accountId", String.valueOf(sub.getAccountId()),
                        "statusAnterior", String.valueOf(sub.getStatus()),
                        "stripeSubscriptionId", sub.getStripeSubscriptionId() == null ? "" : sub.getStripeSubscriptionId()
                ))
                .toList();

        if (!dryRun) {
            Instant now = Instant.now();
            for (AccountSubscription sub : broken) {
                sub.setPlan(PlanType.BASIC);
                sub.setStatus(SubscriptionStatus.ACTIVE);
                sub.setUpdatedAt(now);
            }
            subscriptionRepository.saveAll(broken);
        }

        return Map.of(
                "dryRun", dryRun,
                "encontradas", broken.size(),
                "planAsignado", PlanType.BASIC.name(),
                "cuentas", detail
        );
    }

    private void requireAdmin(String providedSecret) {
        if (adminSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin secret is not configured");
        }
        if (providedSecret == null || !java.security.MessageDigest.isEqual(
                providedSecret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                adminSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin secret");
        }
    }

    private void ensureDemoSubscription(Long accountId) {
        AccountSubscription existing = subscriptionRepository.findByAccountId(accountId).orElse(null);
        if (existing != null) {
            existing.setPlan(PlanType.PRO);
            existing.setStatus(SubscriptionStatus.ACTIVE);
            existing.setUpdatedAt(Instant.now());
            subscriptionRepository.save(existing);
        } else {
            AccountSubscription sub = new AccountSubscription();
            sub.setAccountId(accountId);
            sub.setPlan(PlanType.PRO);
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setUpdatedAt(Instant.now());
            subscriptionRepository.save(sub);
        }
    }

    private String hashPasswordDirect(String password) {
        java.security.SecureRandom secureRandom = new java.security.SecureRandom();
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        try {
            javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    password.toCharArray(), salt, 120_000, 256);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return "pbkdf2$120000$" +
                    java.util.Base64.getEncoder().encodeToString(salt) + "$" +
                    java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash password", e);
        }
    }
}
