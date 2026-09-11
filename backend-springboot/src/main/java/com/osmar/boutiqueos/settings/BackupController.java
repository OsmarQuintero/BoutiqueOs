package com.osmar.boutiqueos.settings;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.subscription.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final AppSettingsService appSettingsService;
    private final AuthSessionService authSessionService;
    private final BackupService backupService;
    private final AccountContext accountContext;
    private final SubscriptionService subscriptionService;

    public BackupController(
            AppSettingsService appSettingsService,
            AuthSessionService authSessionService,
            BackupService backupService,
            AccountContext accountContext,
            SubscriptionService subscriptionService
    ) {
        this.appSettingsService = appSettingsService;
        this.authSessionService = authSessionService;
        this.backupService = backupService;
        this.accountContext = accountContext;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public BackupPayload export(
            @RequestHeader(value = AuthSessionService.SESSION_HEADER, required = false) String token
    ) {
        requireSession(token);
        return backupService.export(accountContext.requireAccountId());
    }

    /**
     * Deja la cuenta como venia en el archivo: borra lo que hay y carga el respaldo.
     *
     * <p>No mezcla. Mezclar duplicaria ventas y descuadraria el inventario, que es
     * peor que no restaurar. Por eso pide el nombre de la tienda en
     * {@code confirmation}: es una operacion que no se puede deshacer.
     */
    @PostMapping("/restore")
    public Map<String, Object> restore(
            @RequestHeader(value = AuthSessionService.SESSION_HEADER, required = false) String token,
            @Valid @RequestBody BackupRestoreRequest request
    ) {
        requireSession(token);

        String storeName = appSettingsService.getCurrent().getStoreName();
        if (!matches(request.confirmation(), storeName)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Para restaurar tienes que escribir el nombre de la tienda tal como esta guardado");
        }

        Map<String, Integer> restored = backupService.restore(
                accountContext.requireAccountId(), request.backup());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("restored", true);
        response.put("counts", restored);
        return response;
    }

    private void requireSession(String token) {
        if (!authSessionService.isValid(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
        subscriptionService.requireFeature("backup");
    }

    private boolean matches(String provided, String storeName) {
        if (provided == null || storeName == null || storeName.isBlank()) {
            return false;
        }
        return provided.trim().toLowerCase(Locale.ROOT)
                .equals(storeName.trim().toLowerCase(Locale.ROOT));
    }
}
