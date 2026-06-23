package com.osmar.boutiqueos.settings;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.util.Base64;

@Service
public class AppSettingsService {

    private static final Long SETTINGS_ID = 1L;
    private static final String PASSWORD_PREFIX = "pbkdf2$";
    private static final int PASSWORD_ITERATIONS = 120_000;
    private static final int PASSWORD_KEY_LENGTH = 256;

    private final AppSettingsRepository appSettingsRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AppSettingsService(AppSettingsRepository appSettingsRepository) {
        this.appSettingsRepository = appSettingsRepository;
    }

    public AppSettings get() {
        return appSettingsRepository.findById(SETTINGS_ID).orElseGet(() -> appSettingsRepository.save(new AppSettings()));
    }

    @Transactional
    public AppSettings update(AppSettingsRequest request) {
        AppSettings settings = get();
        settings.setStoreName(cleanOrDefault(request.storeName(), "Boutique OS"));
        settings.setPhone(cleanOrEmpty(request.phone()));
        settings.setAddress(cleanOrEmpty(request.address()));
        settings.setLogoUrl(cleanOrEmpty(request.logoUrl()));
        settings.setThankYouMessage(cleanOrDefault(request.thankYouMessage(), "Gracias por tu compra"));
        settings.setUsername(cleanOrDefault(request.username(), "admin"));
        if (request.password() != null && !request.password().isBlank()) {
            settings.setPassword(hashPassword(request.password().trim()));
        }
        settings.setUpdatedAt(Instant.now());
        return appSettingsRepository.save(settings);
    }

    @Transactional
    public AppSettings updateTicket(TicketSettingsRequest request) {
        AppSettings settings = get();
        settings.setStoreName(cleanOrDefault(request.storeName(), "Boutique OS"));
        settings.setPhone(cleanOrEmpty(request.phone()));
        settings.setStreet(cleanOrEmpty(request.street()));
        settings.setNeighborhood(cleanOrEmpty(request.neighborhood()));
        settings.setCity(cleanOrEmpty(request.city()));
        settings.setPostalCode(cleanOrEmpty(request.postalCode()));
        settings.setAddress(composeAddress(settings));
        settings.setLogoUrl(cleanOrEmpty(request.logoUrl()));
        settings.setThankYouMessage(cleanOrDefault(request.thankYouMessage(), "Gracias por tu compra"));
        settings.setUpdatedAt(Instant.now());
        return appSettingsRepository.save(settings);
    }

    @Transactional
    public AppSettings updateCredentials(CredentialsSettingsRequest request) {
        AppSettings settings = get();
        if (!passwordMatches(settings.getPassword(), request.currentPassword().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        settings.setUsername(cleanOrDefault(request.username(), "admin"));
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            settings.setPassword(hashPassword(request.newPassword().trim()));
        }
        settings.setUpdatedAt(Instant.now());
        return appSettingsRepository.save(settings);
    }

    public boolean validate(LoginRequest request) {
        AppSettings settings = get();
        boolean valid = settings.getUsername().equals(request.username().trim()) &&
                passwordMatches(settings.getPassword(), request.password().trim());
        if (valid && !isHashed(settings.getPassword())) {
            settings.setPassword(hashPassword(request.password().trim()));
            settings.setUpdatedAt(Instant.now());
            appSettingsRepository.save(settings);
        }
        return valid;
    }

    private String cleanOrDefault(String value, String fallback) {
        String clean = cleanOrEmpty(value);
        return clean.isBlank() ? fallback : clean;
    }

    private String cleanOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String composeAddress(AppSettings settings) {
        return String.join(", ",
                settings.getStreet(),
                settings.getNeighborhood(),
                settings.getCity(),
                settings.getPostalCode()
        ).replaceAll("(,\\s*)+", ", ").replaceAll("^,\\s*|,\\s*$", "");
    }

    private boolean passwordMatches(String storedPassword, String candidate) {
        if (!isHashed(storedPassword)) {
            return storedPassword.equals(candidate);
        }

        String[] parts = storedPassword.split("\\$");
        if (parts.length != 4) {
            return false;
        }

        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        String candidateHash = hashPassword(candidate, salt, iterations);
        return storedPassword.equals(candidateHash);
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return hashPassword(password, salt, PASSWORD_ITERATIONS);
    }

    private String hashPassword(String password, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, PASSWORD_KEY_LENGTH);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return PASSWORD_PREFIX + iterations + "$" +
                    Base64.getEncoder().encodeToString(salt) + "$" +
                    Base64.getEncoder().encodeToString(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash password", exception);
        }
    }

    private boolean isHashed(String password) {
        return password != null && password.startsWith(PASSWORD_PREFIX);
    }
}
