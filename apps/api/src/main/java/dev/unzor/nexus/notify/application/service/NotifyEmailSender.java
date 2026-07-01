package dev.unzor.nexus.notify.application.service;

import dev.unzor.nexus.notify.application.configuration.NotifySmtpProperties;
import dev.unzor.nexus.notify.domain.exception.SmtpNotConfiguredException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * Envía un email por SMTP. Construye el {@code JavaMailSender} una vez a partir
 * de {@link NotifySmtpProperties}; si SMTP (host o from) no está configurado,
 * {@link #send} lanza {@link SmtpNotConfiguredException} para que el servicio
 * marque la notificación como FAILED.
 */
@Component
public class NotifyEmailSender {

    private final JavaMailSenderImpl sender;
    private final String from;

    public NotifyEmailSender(NotifySmtpProperties properties) {
        this.from = properties.from();
        boolean configured = properties.host() != null && !properties.host().isBlank()
                && properties.from() != null && !properties.from().isBlank();
        if (configured) {
            JavaMailSenderImpl impl = new JavaMailSenderImpl();
            impl.setHost(properties.host());
            impl.setPort(properties.port());
            if (properties.username() != null && !properties.username().isBlank()) {
                impl.setUsername(properties.username());
                impl.setPassword(properties.password());
            }
            this.sender = impl;
        } else {
            this.sender = null;
        }
    }

    public boolean isConfigured() {
        return sender != null;
    }

    public void send(String to, String subject, String body) {
        if (sender == null) {
            throw new SmtpNotConfiguredException("SMTP is not configured for this instance.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message); // lanza MailException si falla la entrega
    }
}
