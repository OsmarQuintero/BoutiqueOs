package com.osmar.boutiqueos.onboarding;

import com.osmar.boutiqueos.settings.AppSettingsService;
import com.osmar.boutiqueos.subscription.AccountSubscription;
import com.osmar.boutiqueos.subscription.AccountSubscriptionRepository;
import com.osmar.boutiqueos.subscription.PlanType;
import com.osmar.boutiqueos.subscription.SubscriptionStatus;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class OnboardingService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final OnboardingSessionRepository onboardingSessionRepository;
    private final StripeCheckoutVerifier stripeCheckoutVerifier;
    private final AppSettingsService appSettingsService;
    private final AccountSubscriptionRepository subscriptionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public OnboardingService(
            OnboardingSessionRepository onboardingSessionRepository,
            StripeCheckoutVerifier stripeCheckoutVerifier,
            AppSettingsService appSettingsService,
            AccountSubscriptionRepository subscriptionRepository
    ) {
        this.onboardingSessionRepository = onboardingSessionRepository;
        this.stripeCheckoutVerifier = stripeCheckoutVerifier;
        this.appSettingsService = appSettingsService;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public OnboardingStartResponse start(OnboardingStartRequest request) {
        String sessionId = request.sessionId().trim();
        OnboardingSession existingSession = onboardingSessionRepository.findByStripeSessionId(sessionId).orElse(null);

        if (existingSession != null && existingSession.getConsumedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This payment has already been used");
        }
        if (existingSession != null && existingSession.getExpiresAt().isAfter(Instant.now())) {
            return new OnboardingStartResponse(
                    true,
                    existingSession.getToken(),
                    existingSession.getCustomerEmail(),
                    existingSession.getExpiresAt()
            );
        }

        var stripeDetails = stripeCheckoutVerifier.verifyPaidSession(sessionId);
        OnboardingSession session = existingSession == null
                ? createSession(stripeDetails)
                : extendSession(existingSession, stripeDetails.customerEmail());

        if (session.getConsumedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This payment has already been used");
        }

        return new OnboardingStartResponse(true, session.getToken(), session.getCustomerEmail(), session.getExpiresAt());
    }

    @Transactional
    public OnboardingCompleteResponse complete(OnboardingCompleteRequest request) {
        OnboardingSession session = onboardingSessionRepository.findById(request.token().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Onboarding session not found"));

        if (session.getConsumedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This onboarding link was already used");
        }
        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This onboarding link expired");
        }

        String username = request.email().trim().toLowerCase();
        var account = appSettingsService.completeRegistration(
                request.storeName(),
                request.phone(),
                request.street(),
                request.neighborhood(),
                request.city(),
                request.postalCode(),
                username,
                request.password()
        );

        createSubscriptionForAccount(account.getId(), session);

        session.setConsumedAt(Instant.now());
        session.setAccountId(account.getId());
        onboardingSessionRepository.save(session);

        return new OnboardingCompleteResponse(true, username);
    }

    private void createSubscriptionForAccount(Long accountId, OnboardingSession session) {
        AccountSubscription sub = new AccountSubscription();
        sub.setAccountId(accountId);

        if (session.getPlan() != null && !session.getPlan().isBlank()) {
            try {
                PlanType plan = PlanType.valueOf(session.getPlan());
                sub.setPlan(plan);
                sub.setStatus(SubscriptionStatus.ACTIVE);
            } catch (IllegalArgumentException e) {
                sub.setStatus(SubscriptionStatus.INCOMPLETE);
            }
        } else {
            sub.setStatus(SubscriptionStatus.INCOMPLETE);
        }

        if (session.getStripeCustomerId() != null && !session.getStripeCustomerId().isBlank()) {
            sub.setStripeCustomerId(session.getStripeCustomerId());
        }
        if (session.getStripeSubscriptionId() != null && !session.getStripeSubscriptionId().isBlank()) {
            sub.setStripeSubscriptionId(session.getStripeSubscriptionId());
        }

        subscriptionRepository.save(sub);
    }

    private OnboardingSession createSession(StripeCheckoutVerifier.StripeCheckoutDetails stripeDetails) {
        OnboardingSession session = new OnboardingSession();
        session.setToken(generateToken());
        session.setStripeSessionId(stripeDetails.sessionId());
        session.setCustomerEmail(stripeDetails.customerEmail());
        session.setPlan(stripeDetails.plan());
        session.setStripeCustomerId(stripeDetails.stripeCustomerId());
        session.setStripeSubscriptionId(stripeDetails.stripeSubscriptionId());
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(TOKEN_TTL));
        return onboardingSessionRepository.save(session);
    }

    private OnboardingSession extendSession(OnboardingSession session, String customerEmail) {
        session.setCustomerEmail(customerEmail);
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(TOKEN_TTL));
        session.setConsumedAt(null);
        return onboardingSessionRepository.save(session);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
