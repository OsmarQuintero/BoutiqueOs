package com.osmar.boutiqueos.settings;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AppSettingsServiceTests {

    @Autowired
    private AppSettingsService appSettingsService;

    @Autowired
    private AppSettingsRepository appSettingsRepository;


    @Test
    void unaInstalacionNuevaNoAceptaCredencialesPorDefecto() {
        appSettingsRepository.deleteAll();

        // Antes admin/admin entraba en una base vacia. Ahora no existe cuenta
        // que valga hasta que alguien la provisione con contraseña propia.
        assertFalse(appSettingsService.validate(new LoginRequest("admin", "admin")));
        assertFalse(appSettingsService.validate(new LoginRequest("admin", "")));
        assertFalse(appSettingsService.validate(new LoginRequest("admin", "wrong")));
    }

    @Test
    void laCuentaCreadaAutomaticamenteNaceBloqueada() {
        appSettingsRepository.deleteAll();

        // get() puede crear la cuenta base al resolver configuracion; esa cuenta
        // no debe poder usarse para entrar.
        AppSettings creada = appSettingsService.get();

        assertTrue(creada.getPassword() == null || creada.getPassword().isBlank());
        assertFalse(appSettingsService.validate(new LoginRequest(creada.getUsername(), "admin")));
    }

    @Test
    void provisionOwnerDejaEntrarConLaContrasenaElegida() {
        appSettingsRepository.deleteAll();

        appSettingsService.provisionOwner("osmar@boutique.test", "contrasenaLargaSegura", "Mi Boutique");

        assertTrue(appSettingsService.validate(
                new LoginRequest("osmar@boutique.test", "contrasenaLargaSegura")));
        assertFalse(appSettingsService.validate(new LoginRequest("osmar@boutique.test", "otra")));
        // guardada hasheada, nunca en claro
        assertNotEquals(
                "contrasenaLargaSegura",
                appSettingsRepository.findByUsernameIgnoreCase("osmar@boutique.test").orElseThrow().getPassword());
    }

    @Test
    void updatesCredentialsOnlyWhenCurrentPasswordMatches() {
        appSettingsRepository.deleteAll();
        appSettingsService.provisionOwner("osmar", "contrasenaInicial1", "Mi Boutique");

        appSettingsService.updateCredentials(
                new CredentialsSettingsRequest("osmar", "contrasenaInicial1", "NuevaContrasena1", null));

        assertTrue(appSettingsService.validate(new LoginRequest("osmar", "NuevaContrasena1")));
        assertTrue(appSettingsRepository.existsByUsernameIgnoreCase("osmar"));
        assertNotEquals("NuevaContrasena1", appSettingsService.get().getPassword());
    }

    @Test
    void updatesExtendedTicketSettings() {
        appSettingsRepository.deleteAll();
        appSettingsService.get();

        AppSettings updated = appSettingsService.updateTicket(new TicketSettingsRequest(
                "Boutique Demo",
                "8180000000",
                "Av. Principal 123",
                "Centro",
                "Monterrey",
                "64000",
                "hola@boutique.demo",
                "@boutiquedemo",
                "INSTAGRAM",
                "",
                "Gracias por comprar con nosotras",
                "BDM",
                "Cambios dentro de 15 dias con ticket",
                "THERMAL_58",
                true,
                true,
                true,
                false,
                true,
                false,
                true,
                false
        ));

        assertEquals("Boutique Demo", updated.getStoreName());
        assertEquals("hola@boutique.demo", updated.getContactEmail());
        assertEquals("@boutiquedemo", updated.getInstagramHandle());
        assertEquals("INSTAGRAM", updated.getSocialNetwork());
        assertEquals("BDM", updated.getTicketPrefix());
        assertEquals("THERMAL_58", updated.getTicketPaperSize());
        assertFalse(updated.isShowCustomerOnTicket());
        assertFalse(updated.isShowChangeOnTicket());
        assertTrue(updated.isShowIvaOnTicket());
        assertFalse(updated.isAutoOpenTicket());
    }

}
