package com.osmar.boutiqueos.onboarding;

import com.osmar.boutiqueos.settings.AppSettingsService;
import com.osmar.boutiqueos.subscription.AccountSubscription;
import com.osmar.boutiqueos.subscription.AccountSubscriptionRepository;
import com.osmar.boutiqueos.subscription.PlanResolver;
import com.osmar.boutiqueos.subscription.SubscriptionStatus;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class OnboardingService {

    /**
     * Ventana para activar la cuenta despues de pagar. Es larga a proposito:
     * el enlace tambien llega por correo, y un cliente puede pagar hoy y
     * sentarse a configurar su tienda mañana.
     */
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final OnboardingSessionRepository onboardingSessionRepository;
    private final StripeCheckoutVerifier stripeCheckoutVerifier;
    private final AppSettingsService appSettingsService;
    private final AccountSubscriptionRepository subscriptionRepository;
    private final OnboardingMailService onboardingMailService;
    private final PlanResolver planResolver;
    private final SecureRandom secureRandom = new SecureRandom();

    public OnboardingService(
            OnboardingSessionRepository onboardingSessionRepository,
            StripeCheckoutVerifier stripeCheckoutVerifier,
            AppSettingsService appSettingsService,
            AccountSubscriptionRepository subscriptionRepository,
            OnboardingMailService onboardingMailService,
            PlanResolver planResolver
    ) {
        this.onboardingSessionRepository = onboardingSessionRepository;
        this.stripeCheckoutVerifier = stripeCheckoutVerifier;
        this.appSettingsService = appSettingsService;
        this.subscriptionRepository = subscriptionRepository;
        this.onboardingMailService = onboardingMailService;
        this.planResolver = planResolver;
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

    /**
     * Registra un pago que llego por webhook, sin depender de que el navegador
     * del cliente haya vuelto a la app. Antes de esto, si el cliente cerraba la
     * pestaña despues de pagar el cobro quedaba solo en Stripe y el sistema no
     * se enteraba nunca.
     *
     * <p>Es idempotente: Stripe reintenta los webhooks y una sesion ya
     * registrada o ya consumida no vuelve a notificarse.
     */
    @Transactional
    public void registerPaidCheckout(String stripeSessionId) {
        if (stripeSessionId == null || stripeSessionId.isBlank()) {
            return;
        }

        String sessionId = stripeSessionId.trim();
        OnboardingSession existing = onboardingSessionRepository.findByStripeSessionId(sessionId).orElse(null);

        if (existing != null && existing.getConsumedAt() != null) {
            log.info("Checkout {} ya fue activado; no se reenvia el correo", sessionId);
            return;
        }
        if (existing != null && existing.getExpiresAt().isAfter(Instant.now())) {
            log.info("Checkout {} ya estaba registrado y sigue vigente", sessionId);
            return;
        }

        var stripeDetails = stripeCheckoutVerifier.verifyPaidSession(sessionId);
        OnboardingSession session = existing == null
                ? createSession(stripeDetails)
                : extendSession(existing, stripeDetails.customerEmail());

        onboardingMailService.sendActivationLink(session.getCustomerEmail(), session.getStripeSessionId());
        log.info("Pago registrado por webhook y enlace de activacion enviado para checkout {}", sessionId);
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

        // Llegamos aqui solo despues de que Stripe confirmo el cobro, asi que
        // el cliente siempre queda con un plan usable. Antes, un checkout sin
        // metadata.plan dejaba el plan en null y el sistema le respondia
        // "No tienes una suscripcion activa" aunque estuviera pagando.
        sub.setPlan(planResolver.resolve(session.getPlan(), null));
        sub.setStatus(SubscriptionStatus.ACTIVE);

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
        // Se guarda el plan ya resuelto (metadata, o deducido del precio cobrado)
        // para que al activar la cuenta no haya que volver a adivinar.
        session.setPlan(planResolver.resolve(stripeDetails.plan(), stripeDetails.priceId()).name());
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
