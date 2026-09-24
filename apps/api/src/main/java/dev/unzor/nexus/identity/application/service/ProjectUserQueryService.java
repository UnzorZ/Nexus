package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.api.dto.ProjectUserDetails;
import dev.unzor.nexus.identity.domain.exception.ProjectUserNotFoundException;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lecturas de usuarios de un proyecto (lista y detalle). Sólo lectura,
 * project-scoped.
 */
@Service
public class ProjectUserQueryService {

    private final ProjectUserRepository repository;

    public ProjectUserQueryService(ProjectUserRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ProjectUserDetails> list(UUID projectId) {
        return repository.findAllByProjectId(projectId).stream()
                .map(ProjectUserDetails::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectUserDetails get(UUID projectId, UUID userId) {
        return repository.findByProjectIdAndId(projectId, userId)
                .map(ProjectUserDetails::from)
                .orElseThrow(() -> new ProjectUserNotFoundException(projectId, userId));
    }

    /**
     * El usuario cuyo login OIDC ({@code sub}) es {@code login}: el username si lo
     * tiene, si no el email (ver {@code ProjectUserPrincipal.loginOf}).
     */
    @Transactional(readOnly = true)
    public java.util.Optional<ProjectUserDetails> findByLogin(UUID projectId, String login) {
        String clean = login == null ? "" : login.trim();
        if (clean.isEmpty()) {
            return java.util.Optional.empty();
        }
        return repository.findByProjectIdAndUsername(projectId, clean)
                .or(() -> repository.findByProjectIdAndEmailIgnoreCase(projectId, clean)
                        .filter(user -> user.getUsername() == null))
                .map(ProjectUserDetails::from);
    }
}
