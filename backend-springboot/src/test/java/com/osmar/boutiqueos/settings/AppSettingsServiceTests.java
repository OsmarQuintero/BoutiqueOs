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

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    void validatesDefaultAdminCredentials() {
        appSettingsRepository.deleteAll();

        assertTrue(appSettingsService.validate(new LoginRequest("admin", "admin")));
        assertFalse(appSettingsService.validate(new LoginRequest("admin", "wrong")));
        assertNotEquals("admin", appSettingsService.get().getPassword());
    }

    @Test
    void updatesCredentialsOnlyWhenCurrentPasswordMatches() {
        appSettingsRepository.deleteAll();
        appSettingsService.get();

        appSettingsService.updateCredentials(new CredentialsSettingsRequest("osmar", "admin", "nuevo"));

        assertTrue(appSettingsService.validate(new LoginRequest("osmar", "nuevo")));
        assertTrue(appSettingsRepository.existsByUsernameIgnoreCase("osmar"));
        assertNotEquals("nuevo", appSettingsService.get().getPassword());
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
                false
        ));

        assertEquals("Boutique Demo", updated.getStoreName());
        assertEquals("hola@boutique.demo", updated.getContactEmail());
        assertEquals("@boutiquedemo", updated.getInstagramHandle());
        assertEquals("BDM", updated.getTicketPrefix());
        assertEquals("THERMAL_58", updated.getTicketPaperSize());
        assertFalse(updated.isShowCustomerOnTicket());
        assertFalse(updated.isShowChangeOnTicket());
        assertFalse(updated.isAutoOpenTicket());
    }

    @Test
    void resetsPasswordWithValidToken() {
        appSettingsRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        var settings = appSettingsService.get();
        settings.setUsername("osmar@boutique.test");
        settings.setStoreName("Boutique Demo");
        settings.setPhone("+52 81 2191 8527");
        settings.setPassword("admin");
        appSettingsRepository.save(settings);

        passwordResetService.requestReset("osmar@boutique.test");
        PasswordResetToken token = passwordResetTokenRepository.findAll().getFirst();

        assertTrue(passwordResetService.validateToken(token.getToken()).valid());
        assertTrue(passwordResetService.confirmReset(
                new PasswordResetConfirmRequest(token.getToken(), "NuevaClave1")
        ).updated());
        assertTrue(appSettingsService.validate(new LoginRequest("osmar@boutique.test", "NuevaClave1")));
        assertFalse(appSettingsService.validate(new LoginRequest("osmar@boutique.test", "admin")));
    }

    @Test
    void rejectsWeakPasswordsDuringReset() {
        appSettingsRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        var settings = appSettingsService.get();
        settings.setUsername("osmar@boutique.test");
        settings.setStoreName("Boutique Demo");
        settings.setPhone("8180000000");
        appSettingsRepository.save(settings);

        passwordResetService.requestReset("osmar@boutique.test");
        PasswordResetToken token = passwordResetTokenRepository.findAll().getFirst();

        assertThrows(ResponseStatusException.class, () -> passwordResetService.confirmReset(
                new PasswordResetConfirmRequest(token.getToken(), "debil123")
        ));
    }
}
