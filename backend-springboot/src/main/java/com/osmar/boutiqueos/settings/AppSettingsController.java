package com.osmar.boutiqueos.settings;

import com.osmar.boutiqueos.settings.staff.StaffUser;
import com.osmar.boutiqueos.settings.staff.StaffService;
import java.util.Map;
import com.osmar.boutiqueos.settings.twofactor.TwoFactorService;
import com.osmar.boutiqueos.settings.twofactor.TwoFactorPurpose;
import com.osmar.boutiqueos.settings.twofactor.TwoFactorException;
import com.osmar.boutiqueos.subscription.SubscriptionService;
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
    private final SubscriptionService subscriptionService;
    private final TwoFactorService twoFactorService;
    private final CredentialsService credentialsService;
    private final StaffService staffService;

    public AppSettingsController(
            AppSettingsService appSettingsService,
            AuthSessionService authSessionService,
            LoginAttemptService loginAttemptService,
            PasswordResetService passwordResetService,
            SubscriptionService subscriptionService,
            TwoFactorService twoFactorService,
            CredentialsService credentialsService,
            StaffService staffService
    ) {
        this.appSettingsService = appSettingsService;
        this.authSessionService = authSessionService;
        this.loginAttemptService = loginAttemptService;
        this.passwordResetService = passwordResetService;
        this.subscriptionService = subscriptionService;
        this.twoFactorService = twoFactorService;
        this.credentialsService = credentialsService;
        this.staffService = staffService;
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
        subscriptionService.requireFeature("ticket_customization");
        return AppSettingsResponse.from(appSettingsService.updateTicket(request));
    }

    @PutMapping("/credentials")
    public CredentialsUpdateResponse updateCredentials(
            @RequestHeader(value = AuthSessionService.SESSION_HEADER, required = false) String token,
            @Valid @RequestBody CredentialsSettingsRequest request
    ) {
        SessionInfo session = requireSessionInfo(token);
        String key = credentialsAttemptKey(session.accountId());
        requireNotBlocked(key);
        AppSettings updated;
        try {
            updated = credentialsService.update(session.accountId(), request);
        } catch (CurrentPasswordMismatchException mismatch) {
            recordCredentialsFailure(key);
            throw mismatch;
        }
        loginAttemptService.reset(key);
        // La sesion actual tambien se cerro con el cambio; esta la mantiene dentro.
        return new CredentialsUpdateResponse(
                AppSettingsResponse.from(updated), authSessionService.createSession(session.accountId()));
    }

    /** Paso 1 del cambio de credenciales: confirma la contrasena actual y manda el codigo. */
    @PostMapping("/credentials/code")
    public CredentialsCodeResponse requestCredentialsCode(
            @RequestHeader(value = AuthSessionService.SESSION_HEADER, required = false) String token,
            @Valid @RequestBody CredentialsCodeRequest request
    ) {
        SessionInfo session = requireSessionInfo(token);
        String key = credentialsAttemptKey(session.accountId());
        requireNotBlocked(key);
        try {
            return credentialsService.sendCode(session.accountId(), request.currentPassword());
        } catch (CurrentPasswordMismatchException mismatch) {
            recordCredentialsFailure(key);
            throw mismatch;
        }
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String key = loginAttemptKey(httpRequest, request.username());
        if (loginAttemptService.isBlocked(key)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Try again later.");
        }
        AppSettings account = appSettingsService.authenticate(request);
        if (account != null) {
            loginAttemptService.reset(key);
            return ownerLogin(account, request.deviceToken());
        }
        StaffUser staff = staffService.authenticate(request.username(), request.password());
        if (staff != null) {
            loginAttemptService.reset(key);
            return staffLogin(staff, request.deviceToken());
        }
        loginAttemptService.recordFailure(key);
        return LoginResponse.invalid();
    }

    private LoginResponse ownerLogin(AppSettings account, String deviceToken) {
        String email = appSettingsService.twoFactorEmail(account);
        if (email == null) {
            // Sin correo no hay a donde mandar el codigo: entra, y la pantalla le
            // pide agregar un correo para activar el segundo paso.
            return LoginResponse.signedIn(authSessionService.createSession(account.getId()), null, false, "OWNER", "Duena");
        }
        if (twoFactorService.isTrustedDevice(account.getId(), deviceToken)) {
            return LoginResponse.signedIn(authSessionService.createSession(account.getId()), null, true, "OWNER", "Duena");
        }
        TwoFactorService.Started started = twoFactorService.start(account.getId(), email, TwoFactorPurpose.LOGIN);
        return LoginResponse.challenge(started.challengeId(), started.maskedEmail(), false);
    }

    /**
     * Cuenta de caja: el codigo va al correo de la DUENA. Asi ella autoriza cada
     * dispositivo nuevo de caja y la cajera no necesita correo propio.
     */
    private LoginResponse staffLogin(StaffUser staff, String deviceToken) {
        Long accountId = staff.getAccountId();
        String ownerEmail = appSettingsService.twoFactorEmail(appSettingsService.getByAccountId(accountId));
        if (ownerEmail == null) {
            return LoginResponse.signedIn(authSessionService.createStaffSession(accountId, staff.getId()),
                    null, false, "CASHIER", staff.getName());
        }
        if (twoFactorService.isTrustedDevice(accountId, staff.getId(), deviceToken)) {
            return LoginResponse.signedIn(authSessionService.createStaffSession(accountId, staff.getId()),
                    null, true, "CASHIER", staff.getName());
        }
        TwoFactorService.Started started =
                twoFactorService.start(accountId, staff.getId(), ownerEmail, TwoFactorPurpose.LOGIN);
        return LoginResponse.challenge(started.challengeId(), started.maskedEmail(), true);
    }

    /** Paso 2 del login: el codigo que llego al correo. */
    @PostMapping("/login/verify")
    public LoginResponse verifyLogin(
            @Valid @RequestBody TwoFactorVerifyRequest request,
            HttpServletRequest httpRequest
    ) {
        String key = clientIp(httpRequest) + "|two-factor";
        if (loginAttemptService.isBlocked(key, 20, Duration.ofMinutes(15), Duration.ofMinutes(15))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Espera unos minutos.");
        }
        TwoFactorService.Verified verified;
        try {
            verified = twoFactorService.verifyChallenge(request.challengeId(), request.code(), TwoFactorPurpose.LOGIN);
        } catch (TwoFactorException wrongCode) {
            loginAttemptService.recordFailure(key, 20, Duration.ofMinutes(15), Duration.ofMinutes(15));
            throw wrongCode;
        }
        Long accountId = verified.accountId();
        Long staffId = verified.staffUserId();
        String deviceToken = Boolean.TRUE.equals(request.rememberDevice())
                ? twoFactorService.trustDevice(accountId, staffId, userAgent(httpRequest))
                : null;
        if (staffId == null) {
            return LoginResponse.signedIn(authSessionService.createSession(accountId), deviceToken, true, "OWNER", "Duena");
        }
        String name = staffService.nameOf(staffId);
        return LoginResponse.signedIn(authSessionService.createStaffSession(accountId, staffId), deviceToken, true, "CASHIER", name);
    }

    @PostMapping("/login/resend")
    public Map<String, String> resendLoginCode(@Valid @RequestBody TwoFactorResendRequest request) {
        TwoFactorService.Started started = twoFactorService.resend(request.challengeId(), TwoFactorPurpose.LOGIN);
        return Map.of("challengeId", started.challengeId(), "maskedEmail", started.maskedEmail());
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

    private SessionInfo requireSessionInfo(String token) {
        SessionInfo session = authSessionService.getSession(token);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
        return session;
    }

    // SEC-04: antes se podia probar la contrasena actual sin limite desde una
    // sesion abierta. Ahora 5 fallos bloquean el cambio de credenciales un rato.
    private String credentialsAttemptKey(Long accountId) {
        return "credentials|" + accountId;
    }

    private void requireNotBlocked(String key) {
        if (loginAttemptService.isBlocked(key)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Espera unos minutos.");
        }
    }

    private void recordCredentialsFailure(String key) {
        loginAttemptService.recordFailure(key);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
    }

    private String userAgent(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        return agent == null ? null : agent.trim();
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
