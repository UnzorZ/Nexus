package dev.unzor.nexus.notify.application.service;

import dev.unzor.nexus.notify.application.configuration.NotifySmtpProperties;
import dev.unzor.nexus.notify.domain.entity.ProjectSmtpSettings;
import dev.unzor.nexus.notify.domain.exception.SmtpNotConfiguredException;
import dev.unzor.nexus.notify.persistence.repository.ProjectSmtpSettingsRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Envía email por SMTP resolviendo la configuración por proyecto: si el
 * proyecto tiene su propia configuración SMTP, la usa; si no, cae a la SMTP
 * global de la instancia ({@link NotifySmtpProperties}). Si ninguna está
 * configurada, {@link #send} lanza {@link SmtpNotConfiguredException} para que
 * el servicio marque la notificación como FAILED.
 */
@Component
public class NotifyEmailSender {

    private final NotifySmtpProperties globalProperties;
    private final ProjectSmtpSettingsRepository smtpSettingsRepository;
    private final NotifyCrypto crypto;

    public NotifyEmailSender(NotifySmtpProperties globalProperties,
                             ProjectSmtpSettingsRepository smtpSettingsRepository,
                             NotifyCrypto crypto) {
        this.globalProperties = globalProperties;
        this.smtpSettingsRepository = smtpSettingsRepository;
        this.crypto = crypto;
    }

    /** SMTP efectivo para un proyecto (override del proyecto o global). */
    EffectiveSmtp resolve(UUID projectId) {
        ProjectSmtpSettings settings = smtpSettingsRepository.findByProjectId(projectId).orElse(null);
        if (settings != null) {
            String password = (settings.getPasswordEnc() == null || settings.getPasswordEnc().isBlank())
                    ? null : crypto.decrypt(settings.getPasswordEnc());
            return new EffectiveSmtp(
                    settings.getHost(), settings.getPort(), settings.getUsername(),
                    password, settings.getFromAddress(), true);
        }
        boolean configured = globalProperties.host() != null && !globalProperties.host().isBlank()
                && globalProperties.from() != null && !globalProperties.from().isBlank();
        if (!configured) {
            return new EffectiveSmtp(null, 0, null, null, null, false);
        }
        return new EffectiveSmtp(
                globalProperties.host(), globalProperties.port(), globalProperties.username(),
                globalProperties.password(), globalProperties.from(), true);
    }

    public boolean isConfigured(UUID projectId) {
        return resolve(projectId).configured();
    }

    public void send(UUID projectId, String to, String subject, String body) {
        EffectiveSmtp smtp = resolve(projectId);
        if (!smtp.configured()) {
            throw new SmtpNotConfiguredException("SMTP is not configured for this project.");
        }
        JavaMailSenderImpl sender = build(smtp);
        // HTML: las plantillas son HTML, así que se envía como text/html para que el
        // cliente lo renderice (SimpleMailMessage iría como text/plain -> sin formato).
        MimeMessage mime = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(smtp.from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body == null ? "" : body, true);
        } catch (MessagingException exception) {
            throw new IllegalStateException("Failed to build notification email", exception);
        }
        sender.send(mime); // lanza MailException si falla la entrega
    }

    private JavaMailSenderImpl build(EffectiveSmtp smtp) {
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(smtp.host());
        impl.setPort(smtp.port());
        boolean auth = smtp.username() != null && !smtp.username().isBlank();
        if (auth) {
            impl.setUsername(smtp.username());
            impl.setPassword(smtp.password() == null ? "" : smtp.password());
        }
        java.util.Properties props = impl.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(auth));
        // STARTTLS: el server de Unzor lo anuncia en el :25; sin esto no negocia TLS.
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "false");
        // Trust amplio: necesario para túneles (localhost) y certificados del mail
        // server interno cuyo CN no coincide con el host configurado. Dev/test.
        props.put("mail.smtp.ssl.trust", "*");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return impl;
    }

    /** Configuración SMTP efectiva resuelta para un envío. */
    record EffectiveSmtp(
            String host, int port, String username, String password, String from, boolean configured) {
    }
}
