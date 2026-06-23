package com.osmar.boutiqueos.settings;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/settings")
public class AppSettingsController {

    private final AppSettingsService appSettingsService;
    private final AuthSessionService authSessionService;

    public AppSettingsController(AppSettingsService appSettingsService, AuthSessionService authSessionService) {
        this.appSettingsService = appSettingsService;
        this.authSessionService = authSessionService;
    }

    @GetMapping
    public AppSettingsResponse get(@RequestHeader(value = AuthSessionService.SESSION_HEADER, required = false) String token) {
        requireSession(token);
        return AppSettingsResponse.from(appSettingsService.get());
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
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        if (!appSettingsService.validate(request)) {
            return new LoginResponse(false, null);
        }
        return new LoginResponse(true, authSessionService.createSession());
    }

    private void requireSession(String token) {
        if (!authSessionService.isValid(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
    }
}
