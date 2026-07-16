package com.osmar.boutiqueos.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class PasswordResetMailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetMailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailFrom;
    private final String frontendUrl;

    public PasswordResetMailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:}") String mailFrom,
            @Value("${app.frontend.url:http://localhost:4200}") String frontendUrl
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailFrom = mailFrom == null ? "" : mailFrom.trim();
        this.frontendUrl = frontendUrl == null ? "http://localhost:4200" : frontendUrl.trim();
    }

    public void sendResetLink(String email, String token) {
        String resetUrl = buildResetUrl(token);
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || mailFrom.isBlank()) {
            LOGGER.warn("Password reset email not configured. Reset link for {}: {}", email, resetUrl);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Restablece tu contraseña de Boutique OS");
        message.setText("""
                Recibimos una solicitud para restablecer tu contraseña de Boutique OS.

                Abre este enlace para continuar:
                %s

                Si no fuiste tú, puedes ignorar este correo.
                """.formatted(resetUrl));
        sender.send(message);
    }

    private String buildResetUrl(String token) {
        String separator = frontendUrl.contains("?") ? "&" : "?";
        return frontendUrl + separator + "resetToken=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
