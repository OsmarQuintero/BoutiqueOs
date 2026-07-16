package com.osmar.boutiqueos.settings;

import com.osmar.boutiqueos.config.AccountContext;
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
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AppSettingsService {

    private static final String PASSWORD_PREFIX = "pbkdf2$";
    private static final int PASSWORD_ITERATIONS = 120_000;
    private static final int PASSWORD_KEY_LENGTH = 256;
    private static final Pattern STRONG_PASSWORD = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$");
    private static final Pattern SAFE_DATA_IMAGE = Pattern.compile(
            "^data:image/(png|jpeg|jpg|webp|gif);base64,[a-zA-Z0-9+/=\\r\\n]+$"
    );
    private static final Pattern TICKET_PREFIX = Pattern.compile("[A-Z0-9-]{1,12}");

    private final AppSettingsRepository appSettingsRepository;
    private final AccountContext accountContext;
    private final SecureRandom secureRandom = new SecureRandom();

    public AppSettingsService(AppSettingsRepository appSettingsRepository, AccountContext accountContext) {
        this.appSettingsRepository = appSettingsRepository;
        this.accountContext = accountContext;
    }

    public AppSettings getCurrent() {
        return getByAccountId(resolveCurrentAccountId());
    }

    public AppSettings getByAccountId(Long accountId) {
        return appSettingsRepository.findById(accountId)
                .orElseGet(() -> {
                    if (Long.valueOf(1L).equals(accountId)) {
                        return appSettingsRepository.findByUsernameIgnoreCase("admin")
                                .orElseGet(() -> appSettingsRepository.save(new AppSettings()));
                    }
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
                });
    }

    public AppSettings get() {
        return getCurrent();
    }

    @Transactional
    public AppSettings update(AppSettingsRequest request) {
        AppSettings settings = getCurrent();
        settings.setStoreName(cleanOrDefault(request.storeName(), "Boutique OS"));
        settings.setPhone(cleanOrEmpty(request.phone()));
        settings.setAddress(cleanOrEmpty(request.address()));
        settings.setContactEmail(cleanOrEmpty(request.contactEmail()));
        settings.setInstagramHandle(normalizeInstagramHandle(request.instagramHandle()));
        settings.setLogoUrl(normalizeImageUrl(request.logoUrl()));
        settings.setThankYouMessage(cleanOrDefault(request.thankYouMessage(), "Gracias por tu compra"));
        settings.setTicketPrefix(normalizeTicketPrefix(request.ticketPrefix()));
        settings.setTicketFooterNote(cleanOrEmpty(request.ticketFooterNote()));
        settings.setTicketPaperSize(normalizeTicketPaperSize(request.ticketPaperSize()));
        settings.setShowLogoOnTicket(booleanOrDefault(request.showLogoOnTicket(), true));
        settings.setShowAddressOnTicket(booleanOrDefault(request.showAddressOnTicket(), true));
        settings.setShowPhoneOnTicket(booleanOrDefault(request.showPhoneOnTicket(), true));
        settings.setShowCustomerOnTicket(booleanOrDefault(request.showCustomerOnTicket(), true));
        settings.setShowSavingsOnTicket(booleanOrDefault(request.showSavingsOnTicket(), true));
        settings.setShowChangeOnTicket(booleanOrDefault(request.showChangeOnTicket(), true));
        settings.setAutoOpenTicket(booleanOrDefault(request.autoOpenTicket(), true));
        settings.setUsername(cleanOrDefault(request.username(), "admin"));
        if (request.password() != null && !request.password().isBlank()) {
            settings.setPassword(hashPassword(request.password().trim()));
        }
        settings.setUpdatedAt(Instant.now());
        return appSettingsRepository.save(settings);
    }

    @Transactional
    public AppSettings updateTicket(TicketSettingsRequest request) {
        AppSettings settings = getCurrent();
        settings.setStoreName(cleanOrDefault(request.storeName(), "Boutique OS"));
        settings.setPhone(cleanOrEmpty(request.phone()));
        settings.setStreet(cleanOrEmpty(request.street()));
        settings.setNeighborhood(cleanOrEmpty(request.neighborhood()));
        settings.setCity(cleanOrEmpty(request.city()));
        settings.setPostalCode(cleanOrEmpty(request.postalCode()));
        settings.setAddress(composeAddress(settings));
        settings.setContactEmail(cleanOrEmpty(request.contactEmail()));
        settings.setInstagramHandle(normalizeInstagramHandle(request.instagramHandle()));
        settings.setLogoUrl(normalizeImageUrl(request.logoUrl()));
        settings.setThankYouMessage(cleanOrDefault(request.thankYouMessage(), "Gracias por tu compra"));
        settings.setTicketPrefix(normalizeTicketPrefix(request.ticketPrefix()));
        settings.setTicketFooterNote(cleanOrEmpty(request.ticketFooterNote()));
        settings.setTicketPaperSize(normalizeTicketPaperSize(request.ticketPaperSize()));
        settings.setShowLogoOnTicket(booleanOrDefault(request.showLogoOnTicket(), true));
        settings.setShowAddressOnTicket(booleanOrDefault(request.showAddressOnTicket(), true));
        settings.setShowPhoneOnTicket(booleanOrDefault(request.showPhoneOnTicket(), true));
        settings.setShowCustomerOnTicket(booleanOrDefault(request.showCustomerOnTicket(), true));
        settings.setShowSavingsOnTicket(booleanOrDefault(request.showSavingsOnTicket(), true));
        settings.setShowChangeOnTicket(booleanOrDefault(request.showChangeOnTicket(), true));
        settings.setAutoOpenTicket(booleanOrDefault(request.autoOpenTicket(), true));
        settings.setUpdatedAt(Instant.now());
        return appSettingsRepository.save(settings);
    }

    @Transactional
    public AppSettings updateCredentials(CredentialsSettingsRequest request) {
        AppSettings settings = getCurrent();
        if (!passwordMatches(settings.getPassword(), request.currentPassword().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        String nextUsername = normalizeUsername(request.username());
        if (!settings.getUsername().equalsIgnoreCase(nextUsername) && appSettingsRepository.existsByUsernameIgnoreCase(nextUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        settings.setUsername(nextUsername);
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            settings.setPassword(hashPassword(request.newPassword().trim()));
        }
        settings.setUpdatedAt(Instant.now());
        return appSettingsRepository.save(settings);
    }

    @Transactional
    public AppSettings completeRegistration(
            String storeName,
            String phone,
            String street,
            String neighborhood,
            String city,
            String postalCode,
            String username,
            String password
    ) {
        String normalizedUsername = normalizeUsername(username);
        if (appSettingsRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is already registered");
        }

        AppSettings settings = new AppSettings();
        settings.setStoreName(cleanOrDefault(storeName, "Boutique OS"));
        settings.setPhone(cleanOrEmpty(phone));
        settings.setStreet(cleanOrEmpty(street));
        settings.setNeighborhood(cleanOrEmpty(neighborhood));
        settings.setCity(cleanOrEmpty(city));
        settings.setPostalCode(cleanOrEmpty(postalCode));
        settings.setAddress(composeAddress(settings));
        settings.setUsername(normalizedUsername);
        settings.setPassword(hashPassword(password.trim()));
        Instant now = Instant.now();
        settings.setUpdatedAt(now);
        settings.setRegistrationCompletedAt(now);
        return appSettingsRepository.save(settings);
    }

    @Transactional
    public AppSettings authenticate(LoginRequest request) {
        String normalizedUsername = normalizeUsername(request.username());
        AppSettings settings = appSettingsRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseGet(() -> "admin".equals(normalizedUsername) ? get() : null);
        if (settings == null || !passwordMatches(settings.getPassword(), request.password().trim())) {
            return null;
        }
        if (!isHashed(settings.getPassword())) {
            settings.setPassword(hashPassword(request.password().trim()));
            settings.setUpdatedAt(Instant.now());
            settings = appSettingsRepository.save(settings);
        }
        return settings;
    }

    public boolean validate(LoginRequest request) {
        return authenticate(request) != null;
    }

    @Transactional
    public void updatePasswordForAccount(AppSettings settings, String newPassword) {
        validateStrongPassword(newPassword);
        if (passwordMatches(settings.getPassword(), newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a different password");
        }
        settings.setPassword(hashPassword(newPassword));
        settings.setUpdatedAt(Instant.now());
        appSettingsRepository.save(settings);
    }

    private String cleanOrDefault(String value, String fallback) {
        String clean = cleanOrEmpty(value);
        return clean.isBlank() ? fallback : clean;
    }

    private String normalizeUsername(String value) {
        String clean = cleanOrDefault(value, "admin");
        return clean.toLowerCase(Locale.ROOT);
    }

    private String normalizeInstagramHandle(String value) {
        String clean = cleanOrEmpty(value).replace("@", "");
        return clean.isBlank() ? "" : "@" + clean.replaceAll("\\s+", "");
    }

    private String normalizeTicketPrefix(String value) {
        String clean = cleanOrDefault(value, "BOS").toUpperCase(Locale.ROOT).replaceAll("\\s+", "-");
        clean = clean.replaceAll("[^A-Z0-9-]", "");
        if (!TICKET_PREFIX.matcher(clean).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ticket prefix");
        }
        return clean;
    }

    private String normalizeTicketPaperSize(String value) {
        String clean = cleanOrDefault(value, "THERMAL_80").toUpperCase(Locale.ROOT);
        return switch (clean) {
            case "THERMAL_58", "THERMAL_80", "HALF_LETTER" -> clean;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported ticket paper size");
        };
    }

    private boolean booleanOrDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private void validateStrongPassword(String password) {
        if (!STRONG_PASSWORD.matcher(password).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must include uppercase, lowercase and a number"
            );
        }
    }

    private Long resolveCurrentAccountId() {
        try {
            return accountContext.requireAccountId();
        } catch (ResponseStatusException ignored) {
            return appSettingsRepository.findAll().stream()
                    .findFirst()
                    .orElseGet(() -> appSettingsRepository.save(new AppSettings()))
                    .getId();
        }
    }

    private String cleanOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeImageUrl(String value) {
        String clean = cleanOrEmpty(value);
        if (clean.isBlank()) {
            return "";
        }

        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:text/html") || lower.startsWith("data:image/svg")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image format");
        }

        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return clean;
        }

        if (SAFE_DATA_IMAGE.matcher(clean).matches()) {
            return clean;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image format");
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
