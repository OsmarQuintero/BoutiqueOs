package com.osmar.boutiqueos.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.settings.AppSettings;
import com.osmar.boutiqueos.settings.AppSettingsRepository;
import com.osmar.boutiqueos.settings.AppSettingsService;
import com.osmar.boutiqueos.settings.AuthSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobro sano: el plan y el periodo salen del precio de Stripe, el pago vencido
 * tiene gracia y luego deja la cuenta en solo lectura, cancelar respeta lo pagado,
 * y el webhook sin firma se rechaza.
 */
@SpringBootTest(properties = {
        "app.stripe.price-basic=price_basic_m",
        "app.stripe.price-basic-annual=price_basic_a",
        "app.stripe.price-pro=price_pro_m",
        "app.stripe.price-pro-annual=price_pro_a",
        "app.stripe.webhook-secret=whsec_prueba"
})
@AutoConfigureMockMvc
class BillingTests {

    private static final String SESSION = AuthSessionService.SESSION_HEADER;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private SubscriptionService subscriptionService;
    @Autowired private AccountSubscriptionRepository subscriptionRepository;
    @Autowired private AppSettingsService appSettingsService;
    @Autowired private AppSettingsRepository appSettingsRepository;
    @Autowired private AuthSessionService authSessionService;
    @Autowired private AccountContext accountContext;
    @Autowired private StripePrices stripePrices;
    @Autowired private MockMvc mockMvc;

    private Long accountId;
    private String token;
    private String stripeSub;

    @BeforeEach
    void cuentaPro() {
        accountId = appSettingsService.provisionOwner("cobro-" + System.nanoTime() + "@boutique.test",
                "ClaveSegura2026", "Tienda cobro").getId();
        AppSettings cuenta = appSettingsRepository.findById(accountId).orElseThrow();
        cuenta.setRole("owner");
        appSettingsRepository.save(cuenta);
        stripeSub = "sub_" + System.nanoTime();
        AccountSubscription sub = new AccountSubscription();
        sub.setAccountId(accountId);
        sub.setPlan(PlanType.BASIC);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStripeCustomerId("cus_" + System.nanoTime());
        sub.setStripeSubscriptionId(stripeSub);
        subscriptionRepository.save(sub);
        token = authSessionService.createSession(accountId);
    }

    @AfterEach
    void limpiar() {
        accountContext.clear();
    }

    private AccountSubscription sub() {
        return subscriptionRepository.findByAccountId(accountId).orElseThrow();
    }

    private static JsonNode json(String text) throws Exception {
        return JSON.readTree(text);
    }

    @Test
    void elPrecioDiceElPlanYElPeriodo() {
        assertEquals(new StripePrices.Match(PlanType.PRO, BillingInterval.ANNUAL), stripePrices.resolve("price_pro_a").orElseThrow());
        assertEquals("price_basic_a", stripePrices.priceFor(PlanType.BASIC, BillingInterval.ANNUAL));
        assertTrue(stripePrices.resolve("price_desconocido").isEmpty());
        assertEquals(BillingInterval.ANNUAL, BillingInterval.parse("anual"));
    }

    @Test
    void cambioDePlanDesdeElPortalConLaApiNueva() throws Exception {
        long fin = Instant.now().plus(Duration.ofDays(365)).getEpochSecond();
        // API 2025+: las fechas del periodo vienen en el item, no en la suscripcion.
        subscriptionService.handleWebhookEvent("customer.subscription.updated", json("""
                {"id":"%s","status":"active","cancel_at_period_end":true,
                 "items":{"data":[{"price":{"id":"price_pro_a"},"current_period_start":%d,"current_period_end":%d}]}}
                """.formatted(stripeSub, Instant.now().getEpochSecond(), fin)));

        AccountSubscription s = sub();
        assertEquals(PlanType.PRO, s.getPlan());
        assertEquals(BillingInterval.ANNUAL, s.getBillingInterval());
        assertTrue(s.isCancelAtPeriodEnd());
        assertEquals(fin, s.getCurrentPeriodEnd().getEpochSecond());
    }

    @Test
    void pagoVencidoTieneGraciaYLuegoQuedaEnSoloLectura() throws Exception {
        String fallo = """
                {"parent":{"subscription_details":{"subscription":"%s"}}}
                """.formatted(stripeSub);
        subscriptionService.handleWebhookEvent("invoice.payment_failed", json(fallo));
        assertEquals(SubscriptionStatus.PAST_DUE, sub().getStatus());
        assertNotNull(sub().getPastDueSince());
        assertNull(subscriptionService.writeBlockReason(accountId), "dentro de la gracia se puede vender");

        AccountSubscription vencida = sub();
        vencida.setPastDueSince(Instant.now().minus(Duration.ofDays(SubscriptionService.GRACE_DAYS + 1)));
        subscriptionRepository.save(vencida);
        assertNotNull(subscriptionService.writeBlockReason(accountId));

        mockMvc.perform(post("/api/products").header(SESSION, token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blusa\",\"salePrice\":100}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message").value(containsString("vencido")));
        mockMvc.perform(get("/api/products").header(SESSION, token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/subscription").header(SESSION, token))
                .andExpect(jsonPath("$.accessBlocked").value(true));

        // Paga (actualiza la tarjeta): vuelve a estar al corriente.
        subscriptionService.handleWebhookEvent("invoice.paid", json(fallo));
        assertEquals(SubscriptionStatus.ACTIVE, sub().getStatus());
        assertNull(sub().getPastDueSince());
        mockMvc.perform(post("/api/products").header(SESSION, token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blusa\",\"salePrice\":100}"))
                .andExpect(status().isCreated());
    }

    @Test
    void cancelarRespetaElPeriodoPagadoYAlTerminarQuedaEnSoloLectura() throws Exception {
        accountContext.setAccountId(accountId);
        SubscriptionResponse respuesta = subscriptionService.cancelSubscription();
        assertTrue(respuesta.cancelAtPeriodEnd());
        assertEquals("BASIC", respuesta.plan());
        assertFalse(respuesta.accessBlocked());

        subscriptionService.handleWebhookEvent("customer.subscription.deleted", json("{\"id\":\"" + stripeSub + "\"}"));
        assertNull(sub().getPlan());
        mockMvc.perform(post("/api/products").header(SESSION, token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blusa\",\"salePrice\":100}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message").value(containsString("no esta activa")));
        // Aun asi puede consultar su suscripcion para volver a pagar y bajar su respaldo.
        mockMvc.perform(get("/api/subscription").header(SESSION, token))
                .andExpect(jsonPath("$.accessBlocked").value(true));
        mockMvc.perform(get("/api/backup").header(SESSION, token)).andExpect(status().isOk());
    }

    @Test
    void conSuscripcionVivaNoSeAbreOtroCheckout() {
        accountContext.setAccountId(accountId);
        var conflicto = assertThrows(ResponseStatusException.class,
                () -> subscriptionService.createCheckoutSession(PlanType.PRO, BillingInterval.ANNUAL));
        assertEquals(409, conflicto.getStatusCode().value());
    }

    @Test
    void elWebhookSinFirmaSeRechazaYConFirmaSeProcesa() throws Exception {
        String evento = "{\"type\":\"invoice.paid\",\"data\":{\"object\":{\"subscription\":\"" + stripeSub + "\"}}}";
        mockMvc.perform(post("/api/subscription/webhook").contentType(MediaType.APPLICATION_JSON).content(evento))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/subscription/webhook").contentType(MediaType.APPLICATION_JSON).content(evento)
                        .header("Stripe-Signature", "t=" + Instant.now().getEpochSecond() + ",v1=deadbeef"))
                .andExpect(status().isBadRequest());

        long t = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_prueba".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder hex = new StringBuilder();
        for (byte b : mac.doFinal((t + "." + evento).getBytes(StandardCharsets.UTF_8))) {
            hex.append(String.format("%02x", b));
        }
        mockMvc.perform(post("/api/subscription/webhook").contentType(MediaType.APPLICATION_JSON).content(evento)
                        .header("Stripe-Signature", "t=" + t + ",v1=otra,v1=" + hex))
                .andExpect(status().isOk());
    }
}
