package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.api.dto.ProjectUserDetails;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.domain.exception.ProjectUserEmailAlreadyExistsException;
import dev.unzor.nexus.identity.domain.exception.PublicRegistrationDisabledException;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Alta pública (self-signup) de un usuario final en el realm de un proyecto. Sólo
 * procede si el proyecto tiene {@code publicRegistrationEnabled}. El usuario nace
 * {@code PENDING_VERIFICATION} y recibe un email de verificación; el alta completa la
 * confirma el propio usuario vía token. El email es único por proyecto.
 */
@Service
public class RegisterProjectUserService {

    private final ProjectUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ProjectLookupService projectLookupService;
    private final ProjectUserEmailVerificationService emailVerificationService;
    private final ApplicationEventPublisher eventPublisher;

    public RegisterProjectUserService(
            ProjectUserRepository repository,
            PasswordEncoder passwordEncoder,
            ProjectLookupService projectLookupService,
            ProjectUserEmailVerificationService emailVerificationService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.projectLookupService = projectLookupService;
        this.emailVerificationService = emailVerificationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProjectUserDetails register(
            UUID projectId, String email, String password, String displayName, String username
    ) {
        if (!projectLookupService.isPublicRegistrationEnabled(projectId)) {
            throw new PublicRegistrationDisabledException();
        }
        String normalizedEmail = email.trim();
        if (repository.existsByProjectIdAndEmailIgnoreCase(projectId, normalizedEmail)) {
            throw new ProjectUserEmailAlreadyExistsException(projectId, normalizedEmail);
        }
        ProjectUser user = new ProjectUser(
                projectId, normalizedEmail, passwordEncoder.encode(password), displayName.trim());
        if (username != null && !username.isBlank()) {
            user.updateProfile(user.getDisplayName(), username.trim());
        }
        // Nace PENDING_VERIFICATION (4-arg ctor); se emite el token de verificación.
        ProjectUser saved = repository.save(user);
        emailVerificationService.issueAndSend(saved);
        eventPublisher.publishEvent(AuditEvent.byProjectUser(
                projectId, "project_user.registered", "project_user",
                Objects.toString(saved.getId(), null), saved.getId(), Map.of("email", saved.getEmail())));
        return ProjectUserDetails.from(saved);
    }
}
