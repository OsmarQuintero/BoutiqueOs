package com.osmar.boutiqueos.settings.staff;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.settings.AppSettingsRepository;
import com.osmar.boutiqueos.settings.AppSettingsService;
import com.osmar.boutiqueos.settings.twofactor.TwoFactorService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class StaffService {

    private final StaffUserRepository staffUserRepository;
    private final AppSettingsRepository appSettingsRepository;
    private final AppSettingsService appSettingsService;
    private final TwoFactorService twoFactorService;
    private final AccountContext accountContext;

    public StaffService(
            StaffUserRepository staffUserRepository,
            AppSettingsRepository appSettingsRepository,
            AppSettingsService appSettingsService,
            TwoFactorService twoFactorService,
            AccountContext accountContext
    ) {
        this.staffUserRepository = staffUserRepository;
        this.appSettingsRepository = appSettingsRepository;
        this.appSettingsService = appSettingsService;
        this.twoFactorService = twoFactorService;
        this.accountContext = accountContext;
    }

    public List<StaffUser> list() {
        return staffUserRepository.findAllByAccountIdOrderByCreatedAtAsc(accountContext.requireAccountId());
    }

    @Transactional
    public StaffUser create(StaffRequests.Create request) {
        String username = normalize(request.username());
        // El usuario tiene que ser unico entre duenas Y cajeras: el login busca en ambos.
        if (appSettingsRepository.existsByUsernameIgnoreCase(username) || staffUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese usuario ya existe. Elige otro.");
        }
        StaffUser user = new StaffUser();
        user.setAccountId(accountContext.requireAccountId());
        user.setName(request.name().trim());
        user.setUsername(username);
        user.setPassword(appSettingsService.encodeNewPassword(request.password().trim()));
        user.setMaxDiscountPercent(request.maxDiscountPercent() == null ? 10 : request.maxDiscountPercent());
        return staffUserRepository.save(user);
    }

    @Transactional
    public StaffUser update(Long id, StaffRequests.Update request) {
        StaffUser user = get(id);
        user.setName(request.name().trim());
        if (request.maxDiscountPercent() != null) {
            user.setMaxDiscountPercent(request.maxDiscountPercent());
        }
        if (request.active() != null && request.active() != user.isActive()) {
            user.setActive(request.active());
            if (!request.active()) {
                // Desactivarla la saca de inmediato, no cuando venza su sesion.
                kickOut(user);
            }
        }
        user.setUpdatedAt(Instant.now());
        return staffUserRepository.save(user);
    }

    @Transactional
    public StaffUser resetPassword(Long id, StaffRequests.NewPassword request) {
        StaffUser user = get(id);
        user.setPassword(appSettingsService.encodeNewPassword(request.password().trim()));
        kickOut(user);
        user.setUpdatedAt(Instant.now());
        return staffUserRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        StaffUser user = get(id);
        twoFactorService.forgetStaffDevices(user.getId());
        staffUserRepository.delete(user);
    }

    /** Login de caja. Devuelve null si el usuario no existe, esta desactivado o la contrasena no coincide. */
    @Transactional
    public StaffUser authenticate(String username, String password) {
        StaffUser user = staffUserRepository.findByUsernameIgnoreCase(normalize(username)).orElse(null);
        if (user == null || !user.isActive() || !appSettingsService.passwordMatchesHash(user.getPassword(), password)) {
            return null;
        }
        user.setLastLoginAt(Instant.now());
        return staffUserRepository.save(user);
    }

    public String nameOf(Long staffUserId) {
        return staffUserRepository.findById(staffUserId).map(StaffUser::getName).orElse("Caja");
    }

    private void kickOut(StaffUser user) {
        user.setSessionsValidAfter(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        twoFactorService.forgetStaffDevices(user.getId());
    }

    private StaffUser get(Long id) {
        return staffUserRepository.findByIdAndAccountId(id, accountContext.requireAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario de caja no encontrado"));
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
