package com.osmar.boutiqueos.config;

import com.osmar.boutiqueos.settings.AuthSessionService;
import com.osmar.boutiqueos.settings.SessionInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiSessionInterceptor implements HandlerInterceptor {
    private static final String[] PUBLIC_API_PATHS = {
            "/api/health",
            "/api/checkout/start",
            "/api/settings/login",
            "/api/settings/password-reset/request",
            "/api/settings/login/verify",
            "/api/settings/login/resend",
            "/api/settings/password-reset/confirm",
            "/api/onboarding/start",
            "/api/onboarding/complete",
            "/api/subscription/webhook",
            "/api/admin/demo-account",
            // protegido por X-Admin-Secret, no por sesion de usuario
            "/api/admin/subscriptions/repair",
            "/api/admin/owner-account"
    };

    private final AuthSessionService authSessionService;
    private final AccountContext accountContext;
    private final CurrentUser currentUser;
    private final CashierPolicy cashierPolicy;

    public ApiSessionInterceptor(
            AuthSessionService authSessionService,
            AccountContext accountContext,
            CurrentUser currentUser,
            CashierPolicy cashierPolicy
    ) {
        this.authSessionService = authSessionService;
        this.accountContext = accountContext;
        this.currentUser = currentUser;
        this.cashierPolicy = cashierPolicy;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (isPublicPath(request.getRequestURI())) {
            return true;
        }

        String token = request.getHeader(AuthSessionService.SESSION_HEADER);
        SessionInfo sessionInfo = authSessionService.getSession(token);
        if (sessionInfo == null) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid session");
            return false;
        }
        CurrentUser.Info user = authSessionService.userFor(sessionInfo);
        if (user == null) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid session");
            return false;
        }
        accountContext.setAccountId(sessionInfo.accountId());
        currentUser.set(user);

        if (user.role() == UserRole.CASHIER && cashierPolicy.isForbiddenForCashier(request.getMethod(), request.getRequestURI())) {
            // JSON con mensaje (sendError no lo incluye) para que la caja vea por que.
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\","
                    + "\"message\":\"Tu usuario de caja no tiene permiso para esto. Pideselo a la duena.\"}");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        accountContext.clear();
        currentUser.clear();
    }

    private boolean isPublicPath(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return false;
        }

        for (String publicPath : PUBLIC_API_PATHS) {
            if (requestUri.equals(publicPath)) {
                return true;
            }
        }
        return false;
    }
}
