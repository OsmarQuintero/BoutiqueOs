package com.osmar.boutiqueos.settings;

import com.osmar.boutiqueos.settings.twofactor.TwoFactorException;
import com.osmar.boutiqueos.settings.twofactor.TwoFactorPurpose;
import com.osmar.boutiqueos.settings.twofactor.TwoFactorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambio de usuario o contrasena desde Ajustes, con verificacion en dos pasos.
 *
 * <p>Orden: contrasena actual, luego el codigo que llego al correo ACTUAL, luego
 * el cambio. Si se cambia la contrasena, todos los dispositivos recordados vuelven
 * a pedir codigo y todas las sesiones abiertas se cierran (SEC-05).
 */
@Service
public class CredentialsService {

    private final AppSettingsService appSettingsService;
    private final TwoFactorService twoFactorService;

    public CredentialsService(AppSettingsService appSettingsService, TwoFactorService twoFactorService) {
        this.appSettingsService = appSettingsService;
        this.twoFactorService = twoFactorService;
    }

    public CredentialsCodeResponse sendCode(Long accountId, String currentPassword) {
        AppSettings account = appSettingsService.getByAccountId(accountId);
        if (!appSettingsService.currentPasswordMatches(account, currentPassword)) {
            throw new CurrentPasswordMismatchException();
        }
        String email = appSettingsService.twoFactorEmail(account);
        if (email == null) {
            return new CredentialsCodeResponse(false, null, false);
        }
        var started = twoFactorService.start(accountId, email, TwoFactorPurpose.CHANGE_CREDENTIALS);
        return new CredentialsCodeResponse(true, started.maskedEmail(), true);
    }

    /** noRollbackFor: un codigo incorrecto tiene que dejar contado el intento. */
    @Transactional(noRollbackFor = TwoFactorException.class)
    public AppSettings update(Long accountId, CredentialsSettingsRequest request) {
        AppSettings account = appSettingsService.getByAccountId(accountId);
        if (!appSettingsService.currentPasswordMatches(account, request.currentPassword())) {
            throw new CurrentPasswordMismatchException();
        }
        if (appSettingsService.twoFactorEmail(account) != null) {
            twoFactorService.verifyLatest(accountId, request.code(), TwoFactorPurpose.CHANGE_CREDENTIALS);
        }
        boolean changesPassword = request.newPassword() != null && !request.newPassword().isBlank();
        AppSettings updated = appSettingsService.updateCredentials(request);
        if (changesPassword) {
            twoFactorService.forgetDevices(accountId);
        }
        return updated;
    }
}
