package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.api.dto.ProjectUserDetails;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.domain.exception.ProjectUserEmailAlreadyExistsException;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Alta de un usuario final en el realm de un proyecto. La contraseña la fija el
 * admin y se hashea con el {@code PasswordEncoder} compartido; el usuario nace
 * ACTIVE (verificación de email inmediata). El email es único por proyecto.
 */
@Service
public class CreateProjectUserService {

    private final ProjectUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public CreateProjectUserService(
            ProjectUserRepository repository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProjectUserDetails create(
            UUID projectId, String email, String username, String displayName,
            String password, UUID actorAccountId
    ) {
        String normalizedEmail = email.trim();
        if (repository.existsByProjectIdAndEmailIgnoreCase(projectId, normalizedEmail)) {
            throw new ProjectUserEmailAlreadyExistsException(projectId, normalizedEmail);
        }
        ProjectUser user = new ProjectUser(
                projectId, normalizedEmail, passwordEncoder.encode(password), displayName.trim());
        if (username != null && !username.isBlank()) {
            user.updateProfile(user.getDisplayName(), username.trim());
        }
        // El alta administrativa verifica el email de inmediato (la contraseña la
        // fija el admin); el usuario nace ACTIVE. El flujo de verificación por
        // correo es un enhancement posterior (B3).
        user.verifyEmail(Instant.now());
        ProjectUser saved = repository.save(user);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "project_user.created", "project_user", Objects.toString(saved.getId(), null),
                actorAccountId, Map.of("email", saved.getEmail())));
        return ProjectUserDetails.from(saved);
    }
}
