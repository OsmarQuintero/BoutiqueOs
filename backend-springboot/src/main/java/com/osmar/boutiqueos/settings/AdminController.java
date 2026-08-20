package com.osmar.boutiqueos.settings;

import com.osmar.boutiqueos.subscription.AccountSubscription;
import com.osmar.boutiqueos.subscription.AccountSubscriptionRepository;
import com.osmar.boutiqueos.subscription.PlanType;
import com.osmar.boutiqueos.subscription.SubscriptionStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AppSettingsRepository appSettingsRepository;
    private final AccountSubscriptionRepository subscriptionRepository;

    public AdminController(
            AppSettingsRepository appSettingsRepository,
            AccountSubscriptionRepository subscriptionRepository
    ) {
        this.appSettingsRepository = appSettingsRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @PostMapping("/demo-account")
    public Map<String, String> createDemoAccount() {
        String username = "demo";
        if (appSettingsRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demo account already exists");
        }

        AppSettings settings = new AppSettings();
        settings.setStoreName("Boutique Demo");
        settings.setUsername(username);
        settings.setPassword(hashPassword("demo1234"));
        settings.setRole("admin");
        settings.setUpdatedAt(Instant.now());
        settings.setRegistrationCompletedAt(Instant.now());
        settings = appSettingsRepository.save(settings);

        AccountSubscription sub = new AccountSubscription();
        sub.setAccountId(settings.getId());
        sub.setPlan(PlanType.PRO);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);

        return Map.of(
                "username", username,
                "password", "demo1234",
                "accountId", String.valueOf(settings.getId()),
                "role", "admin",
                "plan", "PRO"
        );
    }

    private String hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            int iterations = 100000;
            int keyLength = 256;
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLength);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return "v1:" + Base64.getEncoder().encodeToString(salt)
                    + ":" + iterations + ":" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash password", e);
        }
    }
}
