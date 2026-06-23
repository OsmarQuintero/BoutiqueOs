package com.osmar.boutiqueos.settings;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AppSettingsServiceTests {

    @Autowired
    private AppSettingsService appSettingsService;

    @Autowired
    private AppSettingsRepository appSettingsRepository;

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
        assertFalse(appSettingsService.validate(new LoginRequest("admin", "admin")));
        assertNotEquals("nuevo", appSettingsService.get().getPassword());
        assertThrows(ResponseStatusException.class, () ->
                appSettingsService.updateCredentials(new CredentialsSettingsRequest("admin", "admin", "admin")));
    }
}
