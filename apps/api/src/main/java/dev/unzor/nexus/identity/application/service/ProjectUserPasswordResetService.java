package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.configuration.IdentityEmailProperties;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.domain.exception.InvalidPasswordResetTokenException;
import dev.unzor.nexus.identity.domain.exception.WeakPasswordException;
import dev.unzor.nexus.identity.infrastructure.IdentityTokens;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.shared.audit.OutboundTransactionalEmail;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reseteo self-service de contraseña por token: el usuario solicita un enlace por
 * email (sin revelar si la cuenta existe) y lo canjea por una nueva contraseña. Al
 * canjear se revocan las sesiones HTTP y se hace bump de {@code authz_version} (los
 * tokens OAuth introspectados dejan de valer — contrato del #24).
 *
 * <p>Sólo las cuentas con email verificado pueden resetear (el propietario del buzón
 * ya está confirmado). Las no verificadas se ignoran silenciosamente.</p>
 */
@Service
public class ProjectUserPasswordResetService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final ProjectUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final EndUserLinkBuilder linkBuilder;
    private final ProjectLookupService projectLookupService;
    private final ProjectUserSessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;
    private final IdentityEmailProperties emailProperties;

    public ProjectUserPasswordResetService(
            ProjectUserRepository repository,
            PasswordEncoder passwordEncoder,
            EndUserLinkBuilder linkBuilder,
            ProjectLookupService projectLookupService,
            ProjectUserSessionService sessionService,
            ApplicationEventPublisher eventPublisher,
            IdentityEmailProperties emailProperties
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.linkBuilder = linkBuilder;
        this.projectLookupService = projectLookupService;
        this.sessionService = sessionService;
        this.eventPublisher = eventPublisher;
        this.emailProperties = emailProperties;
    }

    /**
     * Solicita el reseteo: siempre responde igual (anti-enumeración). Si existe una
     * cuenta verificada para el email, se le envía un enlace de reseteo.
     */
    @Transactional
    public void requestReset(UUID projectId, String email) {
        repository.findByProjectIdAndEmailIgnoreCase(projectId, email.trim())
                .filter(ProjectUser::isEmailVerified)
                .ifPresent(this::issueAndSend);
    }

    private void issueAndSend(ProjectUser user) {
        String slug = projectLookupService.requireSlug(user.getProjectId());
        String rawToken = IdentityTokens.generate();
        user.issuePasswordReset(
                IdentityTokens.hash(rawToken), Instant.now().plus(emailProperties.passwordResetExpiry()));
        repository.save(user);
        String link = linkBuilder.passwordResetLink(slug, rawToken);
        eventPublisher.publishEvent(new OutboundTransactionalEmail(
                user.getProjectId(), user.getEmail(), "Reset your Nexus password", body(link)));
    }

    /**
     * Canjea el token por una nueva contraseña: valida el token (single-use, no
     * expirado), actualiza la contraseña, revoca las sesiones HTTP y hace bump de
     * {@code authz_version}. Lanza si el token es inválido/expirado o la contraseña
     * es débil.
     */
    @Transactional
    public void confirm(UUID projectId, String rawToken, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new WeakPasswordException();
        }
        ProjectUser user = repository
                .findByProjectIdAndPasswordResetTokenHash(projectId, IdentityTokens.hash(rawToken))
                .orElseThrow(InvalidPasswordResetTokenException::new);
        if (user.getPasswordResetExpiresAt() != null
                && user.getPasswordResetExpiresAt().isBefore(Instant.now())) {
            throw new InvalidPasswordResetTokenException();
        }
        user.updatePassword(passwordEncoder.encode(newPassword));
        user.consumePasswordReset();
        user.incrementAuthzVersion(); // los tokens OAuth introspectados dejan de valer (#24)
        repository.save(user);
        sessionService.revokeAll(user.getId()); // mata las sesiones HTTP activas
        eventPublisher.publishEvent(AuditEvent.byProjectUser(
                projectId, "project_user.password_reset", "project_user",
                Objects.toString(user.getId(), null), user.getId(), Map.of("email", user.getEmail())));
    }

    private static String body(String link) {
        return "<p>You requested a password reset. Set a new password:</p>"
                + "<p><a href=\"" + escape(link) + "\">Reset my password</a></p>"
                + "<p style=\"color:#6b7280;font-size:.85rem\">If you did not request this, you can ignore this email.</p>";
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
