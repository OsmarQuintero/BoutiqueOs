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

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AppSettingsService appSettingsService;
    private final AppSettingsRepository appSettingsRepository;
    private final AccountSubscriptionRepository subscriptionRepository;

    public AdminController(
            AppSettingsService appSettingsService,
            AppSettingsRepository appSettingsRepository,
            AccountSubscriptionRepository subscriptionRepository
    ) {
        this.appSettingsService = appSettingsService;
        this.appSettingsRepository = appSettingsRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @PostMapping("/demo-account")
    public Map<String, String> createDemoAccount() {
        String username = "demo";
        if (appSettingsRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demo account already exists");
        }

        AppSettings settings = appSettingsService.completeRegistration(
                "Boutique Demo", "", "", "", "", "", username, "demo1234"
        );
        settings.setRole("admin");
        settings.setUpdatedAt(Instant.now());
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
}
