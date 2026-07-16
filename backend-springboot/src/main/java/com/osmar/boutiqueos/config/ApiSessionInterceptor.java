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
            "/api/checkout/start",
            "/api/settings/login",
            "/api/settings/password-reset/request",
            "/api/settings/password-reset/validate",
            "/api/settings/password-reset/confirm",
            "/api/onboarding/start",
            "/api/onboarding/complete"
    };

    private final AuthSessionService authSessionService;
    private final AccountContext accountContext;

    public ApiSessionInterceptor(AuthSessionService authSessionService, AccountContext accountContext) {
        this.authSessionService = authSessionService;
        this.accountContext = accountContext;
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
        accountContext.setAccountId(sessionInfo.accountId());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        accountContext.clear();
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
