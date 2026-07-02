package dev.unzor.nexus.notify.application.service;

import dev.unzor.nexus.notify.api.dto.SmtpConnectionCheck;
import dev.unzor.nexus.notify.application.configuration.NotifySmtpProperties;
import dev.unzor.nexus.notify.domain.entity.ProjectSmtpSettings;
import dev.unzor.nexus.notify.domain.enums.SmtpTlsMode;
import dev.unzor.nexus.notify.domain.exception.SmtpNotConfiguredException;
import dev.unzor.nexus.notify.domain.exception.UnsafeSmtpHostException;
import dev.unzor.nexus.notify.persistence.repository.ProjectSmtpSettingsRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.UUID;

/**
 * Envía email por SMTP resolviendo la configuración por proyecto: si el
 * proyecto tiene su propia configuración SMTP, la usa; si no, cae a la SMTP
 * global de la instancia ({@link NotifySmtpProperties}). Si ninguna está
 * configurada, {@link #send} lanza {@link SmtpNotConfiguredException} para que
 * el servicio marque la notificación como FAILED.
 *
 * <p>Modelo de confianza TLS (ADR-0013): el modo {@code PUBLIC} verifica contra
 * el truststore público por defecto (WebPKI) con STARTTLS obligatorio — sin
 * {@code mail.smtp.ssl.trust=*}. El modo {@code PINNED} confía sólo en la CA
 * subida por el proyecto mediante un {@link SSLContext} propio inyectado como
 * {@code mail.smtp.ssl.socketFactory}. El host se valida contra SSRF antes de
 * conectar.
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
                    password, settings.getFromAddress(), true,
                    SmtpTlsMode.resolve(settings.getTlsMode()), settings.getTrustedCaPem());
        }
        boolean configured = globalProperties.host() != null && !globalProperties.host().isBlank()
                && globalProperties.from() != null && !globalProperties.from().isBlank();
        if (!configured) {
            return new EffectiveSmtp(null, 0, null, null, null, false, SmtpTlsMode.PUBLIC, null);
        }
        return new EffectiveSmtp(
                globalProperties.host(), globalProperties.port(), globalProperties.username(),
                globalProperties.password(), globalProperties.from(), true, SmtpTlsMode.PUBLIC, null);
    }

    public boolean isConfigured(UUID projectId) {
        return resolve(projectId).configured();
    }

    public void send(UUID projectId, String to, String subject, String body) {
        EffectiveSmtp smtp = resolve(projectId);
        if (!smtp.configured()) {
            throw new SmtpNotConfiguredException("SMTP is not configured for this project.");
        }
        SmtpHostGuard.assertSafe(smtp.host());
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

    /**
     * Comprueba la conexión SMTP: resuelve settings, valida el host (anti-SSRF),
     * abre el transport, negocia STARTTLS verificando el certificado y
     * autentica — sin enviar correo. No lanza: devuelve siempre un resultado.
     */
    public SmtpConnectionCheck testConnection(UUID projectId) {
        EffectiveSmtp smtp = resolve(projectId);
        if (!smtp.configured()) {
            return new SmtpConnectionCheck(false, "SMTP is not configured for this project.");
        }
        try {
            SmtpHostGuard.assertSafe(smtp.host());
        } catch (UnsafeSmtpHostException exception) {
            return new SmtpConnectionCheck(false, exception.getMessage());
        }
        JavaMailSenderImpl sender = build(smtp);
        try {
            Transport transport = sender.getSession().getTransport("smtp");
            if (smtp.username() != null && !smtp.username().isBlank()) {
                transport.connect(smtp.host(), smtp.port(), smtp.username(),
                        smtp.password() == null ? "" : smtp.password());
            } else {
                transport.connect(smtp.host(), smtp.port(), null, null);
            }
            transport.close();
            return new SmtpConnectionCheck(true,
                    "Connected to " + smtp.host() + ":" + smtp.port() + " — TLS verified, authenticated.");
        } catch (Exception exception) {
            return new SmtpConnectionCheck(false, rootMessage(exception));
        }
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
        // STARTTLS obligatorio: si el servidor no anuncia/cierra TLS verificable,
        // el envío fallará antes que bajar a texto plano.
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        // Verificación de hostname (CN/SAN) además de la confianza del certificado.
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        if (smtp.tlsMode() == SmtpTlsMode.PINNED && smtp.trustedCaPem() != null && !smtp.trustedCaPem().isBlank()) {
            // Confiar SÓLO en la CA del proyecto (TrustManager propio). Se inyecta la
            // instancia de SSLSocketFactory: Angus Mail la usa vía la propiedad
            // mail.smtp.ssl.socketFactory (no la *.class global).
            props.put("mail.smtp.ssl.socketFactory", pinnedSocketFactory(smtp.trustedCaPem()));
        }
        // En PUBLIC no se sobreescribe ssl.trust: aplica el truststore público (WebPKI).
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return impl;
    }

    /** Construye un SSLSocketFactory que confía únicamente en la CA PEM dada. */
    private static SSLSocketFactory pinnedSocketFactory(String caPem) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate ca = (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(caPem.getBytes(StandardCharsets.UTF_8)));
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("smtp-ca", ca);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);
            return context.getSocketFactory();
        } catch (Exception exception) {
            // La CA se valida al guardar; si llega aquí está corrupta: no se relaja la
            // confianza, se falla el envío/conexión.
            throw new IllegalStateException("Failed to build pinned SMTP trust store", exception);
        }
    }

    /** Mensaje legible de la causa más profunda (p. ej. PKIX / TLS / auth). */
    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        Throwable deepest = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
            deepest = cursor;
        }
        String message = deepest.getMessage();
        return message != null && !message.isBlank() ? message : deepest.getClass().getSimpleName();
    }

    /** Configuración SMTP efectiva resuelta para un envío/conexión. */
    record EffectiveSmtp(
            String host, int port, String username, String password, String from, boolean configured,
            SmtpTlsMode tlsMode, String trustedCaPem) {
    }
}
