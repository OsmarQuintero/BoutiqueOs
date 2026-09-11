package com.osmar.boutiqueos.settings;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ejercita el endpoint de login por HTTP, no el servicio por dentro.
 *
 * <p>Existe porque las pruebas de settings llamaban directo a
 * {@code AppSettingsService} y se saltaban el controlador, que es donde vive
 * {@code LoginAttemptService}. Por eso el login pudo estar respondiendo 500 en
 * H2 (columna "key", palabra reservada) con las 26 pruebas en verde.
 *
 * <p>El endpoint responde 200 con {@code valid:false} cuando las credenciales
 * no sirven. Lo que se verifica aqui es justamente que responda eso y no un
 * 500: llegar a la respuesta significa que la consulta a login_attempts se
 * ejecuto bien.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginEndpointTests {

    private static final String BAD_LOGIN = """
            {"username":"noexiste@boutique.mx","password":"contrasenaIncorrecta"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void credencialesMalasRespondenValidFalseYNoUn500() throws Exception {
        mockMvc.perform(post("/api/settings/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BAD_LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void variosIntentosSeguidosNoRompenElRegistroDeIntentos() throws Exception {
        // Cada fallo escribe y relee login_attempts. Si la tabla o la columna
        // estan mal, el segundo intento revienta con 500.
        for (int intento = 0; intento < 3; intento++) {
            mockMvc.perform(post("/api/settings/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BAD_LOGIN))
                    .andExpect(status().isOk());
        }
    }
}
