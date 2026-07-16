package com.osmar.boutiqueos.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;
    private final ApiSessionInterceptor apiSessionInterceptor;

    public WebConfig(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String allowedOrigins,
            ApiSessionInterceptor apiSessionInterceptor
    ) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        this.apiSessionInterceptor = apiSessionInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(this.allowedOrigins.length == 0 ? new String[] { "*" } : this.allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*", "X-Boutique-Session");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiSessionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/checkout/start",
                        "/api/settings/login",
                        "/api/settings/password-reset/request",
                        "/api/settings/password-reset/validate",
                        "/api/settings/password-reset/confirm",
                        "/api/onboarding/start",
                        "/api/onboarding/complete"
                );
    }
}
