package com.osmar.boutiqueos.settings;

import com.jayway.jsonpath.JsonPath;
import com.osmar.boutiqueos.settings.twofactor.TwoFactorMailService;
import com.osmar.boutiqueos.settings.twofactor.TwoFactorPurpose;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verificacion en dos pasos por correo y los arreglos SEC-03, SEC-04 y SEC-05,
 * recorriendo los endpoints reales. El correo se simula para poder leer el codigo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TwoFactorAuthTests {

    private static final String PASSWORD = "ClaveSegura2026";
    private static final String SESSION = AuthSessionService.SESSION_HEADER;

    @Autowired private MockMvc mockMvc;
    @Autowired private AppSettingsService appSettingsService;
    @Autowired private AuthSessionService authSessionService;
    @MockitoBean private TwoFactorMailService mailService;

    private String nuevoCorreo() {
        return "duena-" + UUID.randomUUID().toString().substring(0, 8) + "@boutique.test";
    }

    private Long cuenta(String username) {
        return appSettingsService.provisionOwner(username, PASSWORD, "Tienda Dos Pasos").getId();
    }

    private String json(String... pares) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pares.length; i += 2) {
            if (i > 0) out.append(',');
            String value = pares[i + 1];
            out.append('"').append(pares[i]).append("\":")
                    .append(value == null || value.equals("true") || value.equals("false") ? value : "\"" + value + "\"");
        }
        return out.append('}').toString();
    }

    private String loginBody(String user, String password, String deviceToken) throws Exception {
        return mockMvc.perform(post("/api/settings/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", user, "password", password, "deviceToken", deviceToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String ultimoCodigo(String email, TwoFactorPurpose purpose) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailService, atLeastOnce()).sendCode(eq(email), code.capture(), eq(purpose));
        return code.getValue();
    }

    @Test
    void conCorreoElLoginPideCodigoYNoEntregaSesion() throws Exception {
        String email = nuevoCorreo();
        cuenta(email);

        String body = loginBody(email, PASSWORD, null);

        assertNotNull(JsonPath.read(body, "$.challengeId"));
        org.junit.jupiter.api.Assertions.assertEquals(true, JsonPath.read(body, "$.twoFactorRequired"));
        org.junit.jupiter.api.Assertions.assertNull(JsonPath.read(body, "$.token"));
        org.junit.jupiter.api.Assertions.assertTrue(((String) JsonPath.read(body, "$.maskedEmail")).contains("***@boutique.test"));
    }

    @Test
    void elCodigoCorrectoEntregaSesionYRecordarDispositivoSaltaElCodigo() throws Exception {
        String email = nuevoCorreo();
        cuenta(email);
        String challenge = JsonPath.read(loginBody(email, PASSWORD, null), "$.challengeId");

        String verified = mockMvc.perform(post("/api/settings/login/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json("challengeId", challenge, "code", ultimoCodigo(email, TwoFactorPurpose.LOGIN),
                                "rememberDevice", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn().getResponse().getContentAsString();
        String deviceToken = JsonPath.read(verified, "$.deviceToken");
        assertNotNull(deviceToken);

        // Mismo navegador: entra sin codigo.
        clearInvocations(mailService);
        mockMvc.perform(post("/api/settings/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", email, "password", PASSWORD, "deviceToken", deviceToken)))
                .andExpect(jsonPath("$.twoFactorRequired").value(false))
                .andExpect(jsonPath("$.token").isString());
        verify(mailService, never()).sendCode(anyString(), anyString(), eq(TwoFactorPurpose.LOGIN));
    }

    @Test
    void cincoCodigosIncorrectosQuemanElDesafioAunqueDespuesLlegueElCorrecto() throws Exception {
        String email = nuevoCorreo();
        cuenta(email);
        String challenge = JsonPath.read(loginBody(email, PASSWORD, null), "$.challengeId");
        String correcto = ultimoCodigo(email, TwoFactorPurpose.LOGIN);
        String incorrecto = correcto.equals("000000") ? "111111" : "000000";

        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/settings/login/verify").contentType(MediaType.APPLICATION_JSON)
                            .content(json("challengeId", challenge, "code", incorrecto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Te quedan " + (5 - i))));
        }
        mockMvc.perform(post("/api/settings/login/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json("challengeId", challenge, "code", incorrecto)))
                .andExpect(jsonPath("$.message").value(containsString("Demasiados intentos")));

        // El contador se guardo aunque cada intento fallara: el correcto ya no sirve.
        mockMvc.perform(post("/api/settings/login/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json("challengeId", challenge, "code", correcto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unaCuentaSinCorreoEntraDirectoYLaPantallaPuedeAvisar() throws Exception {
        String usuario = "sincorreo" + UUID.randomUUID().toString().substring(0, 6);
        cuenta(usuario);

        mockMvc.perform(post("/api/settings/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", usuario, "password", PASSWORD)))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.twoFactorAvailable").value(false));
    }

    @Test
    void cambiarLaContrasenaPideCodigoYCierraLasDemasSesiones() throws Exception {
        String email = nuevoCorreo();
        Long id = cuenta(email);
        String otraSesion = authSessionService.createSession(id);
        String miSesion = authSessionService.createSession(id);
        // Los tokens llevan la hora con precision de segundos: el cambio tiene que
        // caer en un segundo posterior a su emision.
        Thread.sleep(1100);

        mockMvc.perform(post("/api/settings/credentials/code").header(SESSION, miSesion)
                        .contentType(MediaType.APPLICATION_JSON).content(json("currentPassword", PASSWORD)))
                .andExpect(jsonPath("$.sent").value(true));
        String code = ultimoCodigo(email, TwoFactorPurpose.CHANGE_CREDENTIALS);

        String body = mockMvc.perform(put("/api/settings/credentials").header(SESSION, miSesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", email, "currentPassword", PASSWORD,
                                "newPassword", "OtraClaveSegura2027", "code", code)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String sesionNueva = JsonPath.read(body, "$.token");

        mockMvc.perform(get("/api/settings").header(SESSION, otraSesion)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/settings").header(SESSION, miSesion)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/settings").header(SESSION, sesionNueva)).andExpect(status().isOk());
    }

    @Test
    void sinElCodigoNoSeCambiaLaContrasena() throws Exception {
        String email = nuevoCorreo();
        Long id = cuenta(email);
        String sesion = authSessionService.createSession(id);

        mockMvc.perform(put("/api/settings/credentials").header(SESSION, sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", email, "currentPassword", PASSWORD, "newPassword", "OtraClaveSegura2027")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unaContrasenaNuevaCortaSeRechaza() throws Exception {
        String usuario = "corta" + UUID.randomUUID().toString().substring(0, 6);
        Long id = cuenta(usuario);
        String sesion = authSessionService.createSession(id);

        // Sin correo no hay codigo; aun asi la contrasena nueva tiene que cumplir.
        mockMvc.perform(put("/api/settings/credentials").header(SESSION, sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", usuario, "currentPassword", PASSWORD, "newPassword", "Corta123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("12 caracteres")));
    }

    @Test
    void probarLaContrasenaActualSeBloqueaTrasCincoFallos() throws Exception {
        String usuario = "bloqueo" + UUID.randomUUID().toString().substring(0, 6);
        Long id = cuenta(usuario);
        String sesion = authSessionService.createSession(id);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/settings/credentials/code").header(SESSION, sesion)
                            .contentType(MediaType.APPLICATION_JSON).content(json("currentPassword", "NoEsLaClave99")))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/settings/credentials/code").header(SESSION, sesion)
                        .contentType(MediaType.APPLICATION_JSON).content(json("currentPassword", PASSWORD)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void recuperarLaContrasenaConElCodigoDelCorreo() throws Exception {
        String email = nuevoCorreo();
        cuenta(email);

        mockMvc.perform(post("/api/settings/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", email)))
                .andExpect(jsonPath("$.accepted").value(true));
        String code = ultimoCodigo(email, TwoFactorPurpose.PASSWORD_RESET);

        mockMvc.perform(post("/api/settings/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", email, "code", code, "newPassword", "RecuperadaSegura2026")))
                .andExpect(jsonPath("$.updated").value(true));

        // Con la nueva entra (y vuelve a pedir codigo); con la vieja no.
        mockMvc.perform(post("/api/settings/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", email, "password", "RecuperadaSegura2026")))
                .andExpect(jsonPath("$.twoFactorRequired").value(true));
        mockMvc.perform(post("/api/settings/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", email, "password", PASSWORD)))
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void pedirRecuperacionNoRevelaSiLaCuentaExiste() throws Exception {
        String inventado = nuevoCorreo();

        mockMvc.perform(post("/api/settings/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", inventado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
        verify(mailService, never()).sendCode(eq(inventado), anyString(), eq(TwoFactorPurpose.PASSWORD_RESET));
    }
}
