package com.osmar.boutiqueos.settings;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/settings")
public class AppSettingsController {
    private static final int PASSWORD_RESET_MAX_ATTEMPTS = 3;
    private static final Duration PASSWORD_RESET_WINDOW = Duration.ofMinutes(30);
    private static final Duration PASSWORD_RESET_BLOCK_DURATION = Duration.ofMinutes(45);

    private final AppSettingsService appSettingsService;
    private final AuthSessionService authSessionService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetService passwordResetService;

    public AppSettingsController(
            AppSettingsService appSettingsService,
            AuthSessionService authSessionService,
            LoginAttemptService loginAttemptService,
            PasswordResetService passwordResetService
    ) {
        this.appSettingsService = appSettingsService;
        this.authSessionService = authSessionService;
        this.loginAttemptService = loginAttemptService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping
    public AppSettingsResponse get(@RequestHeader(value = AuthSessionService.SESSION_HEADER, required = false) String token) {
        requireSession(token);
        return AppSettingsResponse.from(appSettingsService.getCurrent());
    }

    @PutMapping
    public AppSettingsResponse update(
            @RequestHeader(value = AuthSessionService.SESSION_HEADER, required = false) String token,
            @Valid @RequestBody AppSettingsRequest request
    ) {
        requireSession(token);
        return AppSettingsResponse.from(appSettingsService.update(request));
    }

    @PutMapping("/ticket")
    public AppSettingsResponse updateTicket(
            @RequestHeader(value = AuthSessionService.SESSION_HEADER, required = false) String token,
            @Valid @RequestBody TicketSettingsRequest request
    ) {
        requireSession(token);
        return AppSettingsResponse.from(appSettingsService.updateTicket(request));
    }

    @PutMapping("/credentials")
    public AppSettingsResponse updateCredentials(
            @RequestHeader(value = AuthSessionService.SESSION_HEADER, required = false) String token,
            @Valid @RequestBody CredentialsSettingsRequest request
    ) {
        requireSession(token);
        return AppSettingsResponse.from(appSettingsService.updateCredentials(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String key = loginAttemptKey(httpRequest, request.username());
        if (loginAttemptService.isBlocked(key)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Try again later.");
        }

        AppSettings account = appSettingsService.authenticate(request);
        if (account == null) {
            loginAttemptService.recordFailure(key);
            return new LoginResponse(false, null);
        }
        loginAttemptService.reset(key);
        return new LoginResponse(true, authSessionService.createSession(account.getId()));
    }

    @PostMapping("/password-reset/request")
    public PasswordResetRequestResponse requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest
    ) {
        String accountKey = passwordResetAttemptKey(httpRequest, request.username());
        String ipKey = passwordResetIpAttemptKey(httpRequest);
        if (loginAttemptService.isBlocked(accountKey, PASSWORD_RESET_MAX_ATTEMPTS, PASSWORD_RESET_WINDOW, PASSWORD_RESET_BLOCK_DURATION)
                || loginAttemptService.isBlocked(ipKey, PASSWORD_RESET_MAX_ATTEMPTS, PASSWORD_RESET_WINDOW, PASSWORD_RESET_BLOCK_DURATION)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many recovery attempts. Try again later.");
        }

        try {
            passwordResetService.requestReset(request.username());
        } finally {
            loginAttemptService.recordFailure(accountKey, PASSWORD_RESET_MAX_ATTEMPTS, PASSWORD_RESET_WINDOW, PASSWORD_RESET_BLOCK_DURATION);
            loginAttemptService.recordFailure(ipKey, PASSWORD_RESET_MAX_ATTEMPTS, PASSWORD_RESET_WINDOW, PASSWORD_RESET_BLOCK_DURATION);
        }
        return new PasswordResetRequestResponse(true);
    }

    @GetMapping("/password-reset/validate")
    public PasswordResetValidateResponse validatePasswordReset(@RequestParam("token") String token) {
        return passwordResetService.validateToken(token);
    }

    @PostMapping("/password-reset/confirm")
    public PasswordResetConfirmResponse confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        return passwordResetService.confirmReset(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = AuthSessionService.SESSION_HEADER, required = false) String token) {
        requireSession(token);
        authSessionService.invalidate(token);
    }

    private void requireSession(String token) {
        if (!authSessionService.isValid(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
    }

    private String loginAttemptKey(HttpServletRequest request, String username) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
        return ip + "|" + username.trim().toLowerCase();
    }

    private String passwordResetAttemptKey(HttpServletRequest request, String username) {
        return loginAttemptKey(request, username) + "|password-reset";
    }

    private String passwordResetIpAttemptKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
        return ip + "|password-reset";
    }
}
