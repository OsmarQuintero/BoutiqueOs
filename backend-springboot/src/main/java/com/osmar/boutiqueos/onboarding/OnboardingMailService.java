package com.osmar.boutiqueos.onboarding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Manda al cliente el enlace para activar su cuenta despues de pagar.
 * Es la red de seguridad del flujo: si cierra la pestaña en el redirect de
 * Stripe, este correo es lo unico que le permite entrar sin intervencion
 * manual.
 */
@Service
public class OnboardingMailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingMailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailFrom;
    private final String frontendUrl;

    public OnboardingMailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:}") String mailFrom,
            @Value("${app.frontend.url:http://localhost:4200}") String frontendUrl
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailFrom = mailFrom == null ? "" : mailFrom.trim();
        this.frontendUrl = frontendUrl == null ? "http://localhost:4200" : frontendUrl.trim().replaceAll("/+$", "");
    }

    public void sendActivationLink(String email, String stripeSessionId) {
        String activationUrl = buildActivationUrl(stripeSessionId);

        if (email == null || email.isBlank()) {
            LOGGER.warn("Checkout sin correo del cliente. Enlace de activacion: {}", activationUrl);
            return;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || mailFrom.isBlank()) {
            // Sin SMTP configurado el enlace queda en el log para poder
            // rescatar la venta a mano en lugar de perderla en silencio.
            LOGGER.warn("Correo no configurado. Enlace de activacion para {}: {}", email, activationUrl);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(email);
            message.setSubject("Activa tu cuenta de Boutique OS");
            message.setText("""
                    Gracias por tu compra. Ya solo falta activar tu cuenta.

                    Abre este enlace para elegir tu contraseña y entrar:
                    %s

                    El enlace estara disponible durante 7 dias. Si necesitas
                    ayuda, responde a este correo.
                    """.formatted(activationUrl));
            sender.send(message);
            LOGGER.info("Enlace de activacion enviado a {}", email);
        } catch (Exception exception) {
            // Un fallo de SMTP no debe tumbar el webhook: Stripe lo reintentaria
            // y el pago ya quedo registrado en la base.
            LOGGER.error("No se pudo enviar el enlace de activacion a {}. Enlace: {}", email, activationUrl, exception);
        }
    }

    private String buildActivationUrl(String stripeSessionId) {
        String separator = frontendUrl.contains("?") ? "&" : "?";
        return frontendUrl + separator + "session_id="
                + URLEncoder.encode(stripeSessionId == null ? "" : stripeSessionId, StandardCharsets.UTF_8);
    }
}
