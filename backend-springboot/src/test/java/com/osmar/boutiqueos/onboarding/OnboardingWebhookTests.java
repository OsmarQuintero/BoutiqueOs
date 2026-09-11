package com.osmar.boutiqueos.onboarding;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre el camino del webhook: un pago hecho desde la landing tiene que quedar
 * registrado aunque el cliente nunca regrese a la app.
 */
@SpringBootTest
class OnboardingWebhookTests {

    private static final String SESSION_ID = "cs_test_webhook_123";
    private static final String EMAIL = "clienta@boutique.mx";

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private OnboardingSessionRepository onboardingSessionRepository;

    @MockitoBean
    private StripeCheckoutVerifier stripeCheckoutVerifier;

    @MockitoBean
    private OnboardingMailService onboardingMailService;

    @BeforeEach
    void stubStripe() {
        when(stripeCheckoutVerifier.verifyPaidSession(SESSION_ID)).thenReturn(
                new StripeCheckoutVerifier.StripeCheckoutDetails(
                        SESSION_ID, EMAIL, "BASIC", "cus_test_1", "sub_test_1", "price_basic_test"
                )
        );
    }

    @AfterEach
    void cleanup() {
        onboardingSessionRepository.deleteAll();
    }

    @Test
    void registraElPagoYMandaElEnlaceCuandoElClienteNuncaVuelve() {
        onboardingService.registerPaidCheckout(SESSION_ID);

        OnboardingSession saved = onboardingSessionRepository.findByStripeSessionId(SESSION_ID).orElseThrow();
        assertEquals(EMAIL, saved.getCustomerEmail());
        assertEquals("BASIC", saved.getPlan());
        assertNotNull(saved.getToken());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now()));

        verify(onboardingMailService).sendActivationLink(EMAIL, SESSION_ID);
    }

    @Test
    void esIdempotentePorqueStripeReintentaLosWebhooks() {
        onboardingService.registerPaidCheckout(SESSION_ID);
        onboardingService.registerPaidCheckout(SESSION_ID);
        onboardingService.registerPaidCheckout(SESSION_ID);

        assertEquals(1, onboardingSessionRepository.count());
        verify(onboardingMailService, times(1)).sendActivationLink(any(), any());
    }

    @Test
    void noReenviaElEnlaceSiLaCuentaYaFueActivada() {
        onboardingService.registerPaidCheckout(SESSION_ID);

        OnboardingSession session = onboardingSessionRepository.findByStripeSessionId(SESSION_ID).orElseThrow();
        session.setConsumedAt(Instant.now());
        onboardingSessionRepository.save(session);

        onboardingService.registerPaidCheckout(SESSION_ID);

        verify(onboardingMailService, times(1)).sendActivationLink(any(), any());
    }

    @Test
    void ignoraEventosSinIdDeSesion() {
        onboardingService.registerPaidCheckout("   ");
        onboardingService.registerPaidCheckout(null);

        assertEquals(0, onboardingSessionRepository.count());
        verify(onboardingMailService, never()).sendActivationLink(any(), any());
    }

    @Test
    void asignaUnPlanAunqueElCheckoutNoTraigaMetadata() {
        // Asi llegaron 10 de los 11 cobros reales: sin metadata.plan. Antes esto
        // dejaba el plan en null y el sistema bloqueaba a un cliente que pagaba.
        String sinPlan = "cs_test_sin_metadata";
        when(stripeCheckoutVerifier.verifyPaidSession(sinPlan)).thenReturn(
                new StripeCheckoutVerifier.StripeCheckoutDetails(
                        sinPlan, EMAIL, null, "cus_test_2", "sub_test_2", null
                )
        );

        onboardingService.registerPaidCheckout(sinPlan);

        OnboardingSession saved = onboardingSessionRepository.findByStripeSessionId(sinPlan).orElseThrow();
        assertEquals("BASIC", saved.getPlan());
    }

    @Test
    void elEnlaceDelCorreoSigueSirviendoParaActivar() {
        onboardingService.registerPaidCheckout(SESSION_ID);

        // El cliente abre el enlace del correo: es el mismo flujo que el
        // redirect de Stripe, asi que /onboarding/start debe devolver el token.
        OnboardingStartResponse response = onboardingService.start(new OnboardingStartRequest(SESSION_ID));

        assertTrue(response.ready());
        assertNotNull(response.onboardingToken());
        assertEquals(EMAIL, response.email());
        verify(stripeCheckoutVerifier, times(1)).verifyPaidSession(eq(SESSION_ID));
    }
}
