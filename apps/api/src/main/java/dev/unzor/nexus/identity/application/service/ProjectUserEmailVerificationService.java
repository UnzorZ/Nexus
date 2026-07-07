package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.configuration.IdentityEmailProperties;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.domain.exception.InvalidEmailVerificationTokenException;
import dev.unzor.nexus.identity.infrastructure.IdentityTokens;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.shared.audit.OutboundTransactionalEmail;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Verificación de email de usuarios finales: emite tokens opacos (hash SHA-256 en
 * almacenamiento), los envía por email, los consume (single-use, con expiración) y
 * permite reenviarlos. El email se entrega vía la tubería genérica
 * {@link OutboundTransactionalEmail} (consumida por el módulo notify).
 */
@Service
public class ProjectUserEmailVerificationService {

    private final ProjectUserRepository repository;
    private final EndUserLinkBuilder linkBuilder;
    private final ProjectLookupService projectLookupService;
    private final ApplicationEventPublisher eventPublisher;
    private final IdentityEmailProperties emailProperties;

    public ProjectUserEmailVerificationService(
            ProjectUserRepository repository,
            EndUserLinkBuilder linkBuilder,
            ProjectLookupService projectLookupService,
            ApplicationEventPublisher eventPublisher,
            IdentityEmailProperties emailProperties
    ) {
        this.repository = repository;
        this.linkBuilder = linkBuilder;
        this.projectLookupService = projectLookupService;
        this.eventPublisher = eventPublisher;
        this.emailProperties = emailProperties;
    }

    /**
     * Genera un token de verificación, lo persiste (hash) y publica el email. El
     * usuario debe existir ya (se persiste de nuevo con el token).
     */
    @Transactional
    public void issueAndSend(ProjectUser user) {
        String slug = projectLookupService.requireSlug(user.getProjectId());
        String rawToken = IdentityTokens.generate();
        Instant expiresAt = Instant.now().plus(emailProperties.verificationExpiry());
        user.issueEmailVerification(IdentityTokens.hash(rawToken), expiresAt);
        repository.save(user);
        String link = linkBuilder.verifyEmailLink(slug, rawToken);
        eventPublisher.publishEvent(new OutboundTransactionalEmail(
                user.getProjectId(), user.getEmail(), "Verify your Nexus email", body(link)));
    }

    /**
     * Consume el token (single-use): verifica el email y activa al usuario. Lanza si el
     * token no existe, ya fue consumido o está expirado (no distingue causa).
     */
    @Transactional
    public ProjectUser verify(UUID projectId, String rawToken) {
        ProjectUser user = repository
                .findByProjectIdAndEmailVerificationTokenHash(projectId, IdentityTokens.hash(rawToken))
                .orElseThrow(InvalidEmailVerificationTokenException::new);
        if (user.getEmailVerificationExpiresAt() != null
                && user.getEmailVerificationExpiresAt().isBefore(Instant.now())) {
            throw new InvalidEmailVerificationTokenException();
        }
        user.consumeEmailVerification(Instant.now());
        repository.save(user);
        eventPublisher.publishEvent(AuditEvent.byProjectUser(
                projectId, "project_user.email_verified", "project_user",
                Objects.toString(user.getId(), null), user.getId(), Map.of("email", user.getEmail())));
        return user;
    }

    /**
     * Reenvía la verificación si el email existe y aún no está verificado. No revela
     * si el email existe (anti-enumeración): ausente o ya verificado → no-op.
     */
    @Transactional
    public void resend(UUID projectId, String email) {
        repository.findByProjectIdAndEmailIgnoreCase(projectId, email.trim())
                .filter(u -> !u.isEmailVerified())
                .ifPresent(this::issueAndSend);
    }

    private static String body(String link) {
        return "<p>Confirm your email address to activate your account:</p>"
                + "<p><a href=\"" + escape(link) + "\">Verify my email</a></p>"
                + "<p style=\"color:#6b7280;font-size:.85rem\">If you did not create an account, you can ignore this email.</p>";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
