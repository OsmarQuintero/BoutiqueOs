package com.osmar.boutiqueos.settings;

import com.osmar.boutiqueos.settings.twofactor.TwoFactorException;
import com.osmar.boutiqueos.settings.twofactor.TwoFactorPurpose;
import com.osmar.boutiqueos.settings.twofactor.TwoFactorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperacion de contrasena con un codigo por correo (antes era un enlace).
 *
 * <p>Nunca dice si la cuenta existe: pedir un codigo para un usuario inventado
 * responde igual que para uno real, para que no se puedan adivinar usuarios.
 */
@Service
public class PasswordResetService {

    private final AppSettingsService appSettingsService;
    private final TwoFactorService twoFactorService;

    public PasswordResetService(AppSettingsService appSettingsService, TwoFactorService twoFactorService) {
        this.appSettingsService = appSettingsService;
        this.twoFactorService = twoFactorService;
    }

    public void requestReset(String username) {
        appSettingsService.findByUsername(username).ifPresent(account -> {
            String email = appSettingsService.twoFactorEmail(account);
            if (email != null) {
                twoFactorService.start(account.getId(), email, TwoFactorPurpose.PASSWORD_RESET);
            }
        });
    }

    /**
     * noRollbackFor: si el codigo es incorrecto, el intento tiene que quedar
     * contado. Si lo que falla es la contrasena nueva (muy corta), si se revierte
     * y el mismo codigo sirve para reintentar.
     */
    @Transactional(noRollbackFor = TwoFactorException.class)
    public PasswordResetConfirmResponse confirmReset(PasswordResetConfirmRequest request) {
        AppSettings account = appSettingsService.findByUsername(request.username())
                .orElseThrow(() -> new TwoFactorException("Codigo incorrecto o vencido"));
        twoFactorService.verifyLatest(account.getId(), request.code(), TwoFactorPurpose.PASSWORD_RESET);
        appSettingsService.updatePasswordForAccount(account, request.newPassword().trim());
        twoFactorService.forgetDevices(account.getId());
        return new PasswordResetConfirmResponse(true, account.getUsername());
    }
}
