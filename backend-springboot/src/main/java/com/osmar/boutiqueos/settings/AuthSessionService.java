package com.osmar.boutiqueos.settings;

import com.osmar.boutiqueos.config.CurrentUser;
import com.osmar.boutiqueos.config.UserRole;
import com.osmar.boutiqueos.settings.staff.StaffUser;
import com.osmar.boutiqueos.settings.staff.StaffUserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthSessionService {

    public static final String SESSION_HEADER = "X-Boutique-Session";

    private final JwtTokenService jwtTokenService;
    private final TokenBlocklist tokenBlocklist;
    private final AppSettingsRepository appSettingsRepository;
    private final StaffUserRepository staffUserRepository;

    public AuthSessionService(
            JwtTokenService jwtTokenService,
            TokenBlocklist tokenBlocklist,
            AppSettingsRepository appSettingsRepository,
            StaffUserRepository staffUserRepository
    ) {
        this.jwtTokenService = jwtTokenService;
        this.tokenBlocklist = tokenBlocklist;
        this.appSettingsRepository = appSettingsRepository;
        this.staffUserRepository = staffUserRepository;
    }

    public String createSession(Long accountId) {
        return jwtTokenService.createToken(accountId);
    }

    public String createStaffSession(Long accountId, Long staffUserId) {
        return jwtTokenService.createToken(accountId, staffUserId);
    }

    /** Quien es el dueno del token, para los permisos de la peticion. */
    public CurrentUser.Info userFor(SessionInfo info) {
        if (!info.isStaff()) {
            return new CurrentUser.Info(UserRole.OWNER, null, "Dueña", null);
        }
        return staffUserRepository.findById(info.staffUserId())
                .map(staff -> new CurrentUser.Info(UserRole.CASHIER, staff.getId(), staff.getName(), staff.getMaxDiscountPercent()))
                .orElse(null);
    }

    public boolean isValid(String token) {
        return getSession(token) != null;
    }

    public SessionInfo getSession(String token) {
        if (tokenBlocklist.isRevoked(token)) {
            return null;
        }
        SessionInfo info = jwtTokenService.parseToken(token);
        if (info == null) {
            return null;
        }
        // SEC-05: al cambiar la contrasena, cualquier token emitido antes deja de
        // valer. Antes el cambio no cerraba nada: quien tuviera una sesion robada
        // seguia dentro hasta que el token venciera solo.
        if (info.isStaff()) {
            // Cuenta de caja: tiene que seguir existiendo, ser de esta tienda y estar
            // activa. Desactivarla o cambiarle la contrasena la saca de inmediato.
            StaffUser staff = staffUserRepository.findById(info.staffUserId()).orElse(null);
            if (staff == null || !staff.isActive() || !staff.getAccountId().equals(info.accountId())) {
                return null;
            }
            Instant staffValidAfter = staff.getSessionsValidAfter();
            if (staffValidAfter != null && (info.issuedAt() == null || info.issuedAt().isBefore(staffValidAfter))) {
                return null;
            }
            return info;
        }
        Instant validAfter = appSettingsRepository.findById(info.accountId())
                .map(AppSettings::getSessionsValidAfter)
                .orElse(null);
        if (validAfter != null && (info.issuedAt() == null || info.issuedAt().isBefore(validAfter))) {
            return null;
        }
        return info;
    }

    public void invalidate(String token) {
        if (token != null && !token.isBlank()) {
            SessionInfo info = jwtTokenService.parseToken(token);
            if (info != null) {
                tokenBlocklist.revoke(token, info.expiresAt());
            }
        }
    }
}
