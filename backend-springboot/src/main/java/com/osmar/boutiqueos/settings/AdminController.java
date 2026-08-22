package com.osmar.boutiqueos.settings;

import com.osmar.boutiqueos.subscription.AccountSubscription;
import com.osmar.boutiqueos.subscription.AccountSubscriptionRepository;
import com.osmar.boutiqueos.subscription.PlanType;
import com.osmar.boutiqueos.subscription.SubscriptionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final String ADMIN_SECRET_HEADER = "X-Admin-Secret";

    private final AppSettingsRepository appSettingsRepository;
    private final AccountSubscriptionRepository subscriptionRepository;
    private final String adminSecret;

    public AdminController(
            AppSettingsRepository appSettingsRepository,
            AccountSubscriptionRepository subscriptionRepository,
            @Value("${app.admin.secret:}") String adminSecret
    ) {
        this.appSettingsRepository = appSettingsRepository;
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
