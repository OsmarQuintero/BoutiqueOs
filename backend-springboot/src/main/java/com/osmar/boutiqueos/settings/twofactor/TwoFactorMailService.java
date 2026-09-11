package com.osmar.boutiqueos.settings.twofactor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Manda el codigo de verificacion por correo. */
@Service
public class TwoFactorMailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwoFactorMailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailFrom;

    public TwoFactorMailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:}") String mailFrom
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailFrom = mailFrom == null ? "" : mailFrom.trim();
    }

    public void sendCode(String email, String code, TwoFactorPurpose purpose) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || mailFrom.isBlank()) {
            // Sin SMTP (desarrollo local) el codigo va al log para poder entrar.
            // En produccion SMTP es obligatorio: ver DEPLOY.md.
            LOGGER.warn("Correo no configurado. Codigo de verificacion ({}) para {}: {}", purpose, email, code);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject(subject(purpose) + " - " + code);
        message.setText("""
                Tu codigo de verificacion de Boutique OS es:

                    %s

                Vence en 10 minutos. %s

                Si no fuiste tu, ignora este correo y cambia tu contrasena.
                """.formatted(code, reason(purpose)));
        sender.send(message);
    }

    private String subject(TwoFactorPurpose purpose) {
        return switch (purpose) {
            case LOGIN -> "Codigo para entrar a Boutique OS";
            case PASSWORD_RESET -> "Codigo para recuperar tu contrasena";
            case CHANGE_CREDENTIALS -> "Codigo para cambiar tu acceso";
        };
    }

    private String reason(TwoFactorPurpose purpose) {
        return switch (purpose) {
            case LOGIN -> "Alguien esta entrando a tu cuenta desde un dispositivo nuevo.";
            case PASSWORD_RESET -> "Pediste recuperar tu contrasena.";
            case CHANGE_CREDENTIALS -> "Pediste cambiar tu usuario o contrasena.";
        };
    }
}
