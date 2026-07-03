package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.api.dto.ProjectUserDetails;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.domain.exception.ProjectUserNotFoundException;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reset administrativo de contraseña: el admin fija una nueva contraseña (en
 * claro), el servicio la hashea y la reemplaza. Sin flujo de token por correo
 * en B1 (B3).
 */
@Service
public class ResetProjectUserPasswordService {

    private final ProjectUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public ResetProjectUserPasswordService(
            ProjectUserRepository repository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProjectUserDetails reset(UUID projectId, UUID userId, String newPassword, UUID actorAccountId) {
        ProjectUser user = repository.findByProjectIdAndId(projectId, userId)
                .orElseThrow(() -> new ProjectUserNotFoundException(projectId, userId));
        user.updatePassword(passwordEncoder.encode(newPassword));
        ProjectUser saved = repository.save(user);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "project_user.password_reset", "project_user", Objects.toString(saved.getId(), null),
                actorAccountId, Map.of()));
        return ProjectUserDetails.from(saved);
    }
}
