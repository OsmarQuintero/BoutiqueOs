package com.osmar.boutiqueos.settings.staff;

import com.jayway.jsonpath.JsonPath;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.product.ProductStatus;
import com.osmar.boutiqueos.settings.AppSettings;
import com.osmar.boutiqueos.settings.AppSettingsRepository;
import com.osmar.boutiqueos.settings.AppSettingsService;
import com.osmar.boutiqueos.settings.AuthSessionService;
import com.osmar.boutiqueos.settings.twofactor.TwoFactorMailService;
import com.osmar.boutiqueos.settings.twofactor.TwoFactorPurpose;
import com.osmar.boutiqueos.subscription.AccountSubscription;
import com.osmar.boutiqueos.subscription.AccountSubscriptionRepository;
import com.osmar.boutiqueos.subscription.PlanType;
import com.osmar.boutiqueos.subscription.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cuentas de caja: las crea la duena (plan Pro), entran con un codigo que le llega
 * a la duena, cobran con su nombre, tienen tope de descuento y no pueden tocar lo
 * que la politica de caja les niega.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaffAccessTests {

    private static final String SESSION = AuthSessionService.SESSION_HEADER;
    private static final String OWNER_PASSWORD = "DuenaSegura2026";
    private static final String CASHIER_PASSWORD = "CajaSegura20261";

    @Autowired private MockMvc mockMvc;
    @Autowired private AppSettingsService appSettingsService;
    @Autowired private AppSettingsRepository appSettingsRepository;
    @Autowired private AuthSessionService authSessionService;
    @Autowired private AccountSubscriptionRepository subscriptionRepository;
    @Autowired private ProductRepository productRepository;
    @MockitoBean private TwoFactorMailService mailService;

    private String ownerEmail;
    private Long accountId;
    private String ownerToken;
    private Product vestido;

    @BeforeEach
    void tiendaPro() {
        ownerEmail = "duena-" + UUID.randomUUID().toString().substring(0, 8) + "@boutique.test";
        accountId = appSettingsService.provisionOwner(ownerEmail, OWNER_PASSWORD, "Tienda con caja").getId();
        // provisionOwner la marca "admin" (cuenta demo, sin limites de plan). Aqui
        // hace falta una clienta normal para que el plan si cuente.
        AppSettings account = appSettingsRepository.findById(accountId).orElseThrow();
        account.setRole("owner");
        appSettingsRepository.save(account);
        plan(accountId, PlanType.PRO);
        ownerToken = authSessionService.createSession(accountId);

        vestido = new Product();
        vestido.setAccountId(accountId);
        vestido.setName("Vestido caja");
        vestido.setCategory("Vestidos");
        vestido.setSku("CAJA-" + System.nanoTime());
        vestido.setCostPrice(new BigDecimal("200.00"));
        vestido.setSalePrice(new BigDecimal("500.00"));
        vestido.setStock(10);
        vestido.setStatus(ProductStatus.ACTIVE);
        vestido = productRepository.save(vestido);
    }

    private void plan(Long id, PlanType type) {
        AccountSubscription sub = subscriptionRepository.findByAccountId(id).orElseGet(AccountSubscription::new);
        sub.setAccountId(id);
        sub.setPlan(type);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);
    }

    private String crearCajera(String username, int maxDiscount) throws Exception {
        String body = mockMvc.perform(post("/api/staff").header(SESSION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lupita\",\"username\":\"" + username + "\",\"password\":\""
                                + CASHIER_PASSWORD + "\",\"maxDiscountPercent\":" + maxDiscount + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return String.valueOf((Object) JsonPath.read(body, "$.id"));
    }

    /** Entra como cajera: el codigo le llega a la duena. Devuelve el token de caja. */
    private String entrarComoCajera(String username) throws Exception {
        String login = mockMvc.perform(post("/api/settings/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + CASHIER_PASSWORD + "\"}"))
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.codeSentToOwner").value(true))
                .andReturn().getResponse().getContentAsString();
        String challenge = JsonPath.read(login, "$.challengeId");
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailService, atLeastOnce()).sendCode(eq(ownerEmail), code.capture(), eq(TwoFactorPurpose.LOGIN));
        String verified = mockMvc.perform(post("/api/settings/login/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + challenge + "\",\"code\":\"" + code.getValue() + "\"}"))
                .andExpect(jsonPath("$.role").value("CASHIER"))
                .andExpect(jsonPath("$.displayName").value("Lupita"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(verified, "$.token");
    }

    private String usuario() {
        return "caja-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void laCajeraEntraConCodigoDeLaDuenaYCobraConSuNombre() throws Exception {
        String username = usuario();
        crearCajera(username, 10);
        String token = entrarComoCajera(username);

        mockMvc.perform(post("/api/sales").header(SESSION, token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"items\":[{\"productId\":" + vestido.getId() + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.soldByName").value("Lupita"));
    }

    @Test
    void laCajeraNoPuedeTocarLoQueLeCorrespondeALaDuena() throws Exception {
        String username = usuario();
        crearCajera(username, 10);
        String token = entrarComoCajera(username);

        mockMvc.perform(put("/api/products/" + vestido.getId()).header(SESSION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Precio cambiado\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("usuario de caja")));
        mockMvc.perform(get("/api/backup").header(SESSION, token)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff").header(SESSION, token)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/sales/1/refund").header(SESSION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // Consultar si puede.
        mockMvc.perform(get("/api/products").header(SESSION, token)).andExpect(status().isOk());
    }

    @Test
    void elDescuentoManualDeLaCajeraTieneTope() throws Exception {
        String username = usuario();
        crearCajera(username, 10);
        String token = entrarComoCajera(username);
        String base = "{\"paymentMethod\":\"CASH\",\"items\":[{\"productId\":" + vestido.getId() + ",\"quantity\":1}],\"manualDiscount\":";

        // 10% de $500 = $50: 80 no, 50 si.
        mockMvc.perform(post("/api/sales").header(SESSION, token).contentType(MediaType.APPLICATION_JSON).content(base + "80}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("10%")));
        mockMvc.perform(post("/api/sales").header(SESSION, token).contentType(MediaType.APPLICATION_JSON).content(base + "50}"))
                .andExpect(status().isCreated());
    }

    @Test
    void desactivarALaCajeraLaSacaDeInmediato() throws Exception {
        String username = usuario();
        String id = crearCajera(username, 10);
        String token = entrarComoCajera(username);
        mockMvc.perform(get("/api/products").header(SESSION, token)).andExpect(status().isOk());

        mockMvc.perform(put("/api/staff/" + id).header(SESSION, ownerToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lupita\",\"active\":false}"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/products").header(SESSION, token)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/settings/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + CASHIER_PASSWORD + "\"}"))
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void enElPlanBasicoNoHayUsuariosDeCaja() throws Exception {
        plan(accountId, PlanType.BASIC);
        mockMvc.perform(post("/api/staff").header(SESSION, ownerToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lupita\",\"username\":\"" + usuario() + "\",\"password\":\"" + CASHIER_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void elUsuarioDeCajaNoPuedeRepetirElDeLaDuena() throws Exception {
        mockMvc.perform(post("/api/staff").header(SESSION, ownerToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Copia\",\"username\":\"" + ownerEmail + "\",\"password\":\"" + CASHIER_PASSWORD + "\"}"))
                .andExpect(status().isConflict());
    }
}
