package com.osmar.boutiqueos.settings;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class PasswordResetService {
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AppSettingsRepository appSettingsRepository;
    private final AppSettingsService appSettingsService;
    private final PasswordResetMailService passwordResetMailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration tokenTtl;

    public PasswordResetService(
            PasswordResetTokenRepository passwordResetTokenRepository,
            AppSettingsRepository appSettingsRepository,
            AppSettingsService appSettingsService,
            PasswordResetMailService passwordResetMailService,
            @Value("${app.password-reset.ttl-minutes:20}") long ttlMinutes
    ) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.appSettingsRepository = appSettingsRepository;
        this.appSettingsService = appSettingsService;
        this.passwordResetMailService = passwordResetMailService;
        this.tokenTtl = Duration.ofMinutes(Math.max(5, ttlMinutes));
    }

    @Transactional
    public void requestReset(String username) {
        passwordResetTokenRepository.deleteByExpiresAtBefore(Instant.now());
        AppSettings account = appSettingsRepository.findByUsernameIgnoreCase(username.trim().toLowerCase()).orElse(null);
        if (account == null) {
            return;
        }

        passwordResetTokenRepository.deleteByAccountId(account.getId());
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(generateToken());
        token.setAccountId(account.getId());
        token.setEmail(account.getUsername());
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(tokenTtl));
        passwordResetTokenRepository.save(token);
        passwordResetMailService.sendResetLink(account.getUsername(), token.getToken());
    }

    public PasswordResetValidateResponse validateToken(String rawToken) {
        PasswordResetToken token = requireValidToken(rawToken);
        return new PasswordResetValidateResponse(true, maskEmail(token.getEmail()), token.getExpiresAt().toString());
    }

    @Transactional
    public PasswordResetConfirmResponse confirmReset(PasswordResetConfirmRequest request) {
        PasswordResetToken token = requireValidToken(request.token());
        AppSettings account = appSettingsRepository.findById(token.getAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        appSettingsService.updatePasswordForAccount(account, request.newPassword().trim());
        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);
        passwordResetTokenRepository.deleteByAccountId(account.getId());
        return new PasswordResetConfirmResponse(true, account.getUsername());
    }

    private PasswordResetToken requireValidToken(String rawToken) {
        String tokenValue = rawToken == null ? "" : rawToken.trim();
        PasswordResetToken token = passwordResetTokenRepository.findById(tokenValue)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reset token not found"));
        Instant now = Instant.now();
        if (token.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Reset token already used");
        }
        if (token.getExpiresAt().isBefore(now)) {
            passwordResetTokenRepository.delete(token);
            throw new ResponseStatusException(HttpStatus.GONE, "Reset token expired");
        }
        return token;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "*" + domain;
        }
        return name.substring(0, 2) + "*".repeat(Math.max(1, name.length() - 2)) + domain;
    }
}
