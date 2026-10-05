package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.api.dto.ProjectUserDetails;
import dev.unzor.nexus.identity.api.dto.RuntimeProjectUserDetails;
import dev.unzor.nexus.identity.domain.exception.ProjectUserNotFoundException;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.permissions.application.service.EffectiveAuthoritiesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecturas de usuarios de un proyecto (lista y detalle). Sólo lectura,
 * project-scoped.
 *
 * <p>Las variantes {@code runtime} añaden las claves de los roles del usuario en
 * ese proyecto, resueltas con el mismo {@link EffectiveAuthoritiesService} que el
 * claim {@code roles} del ID token para que ambos valores coincidan siempre.</p>
 */
@Service
public class ProjectUserQueryService {

    private final ProjectUserRepository repository;
    private final EffectiveAuthoritiesService effectiveAuthoritiesService;

    public ProjectUserQueryService(
            ProjectUserRepository repository,
            EffectiveAuthoritiesService effectiveAuthoritiesService
    ) {
        this.repository = repository;
        this.effectiveAuthoritiesService = effectiveAuthoritiesService;
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

    /** Como {@link #get(UUID, UUID)} incluyendo los roles del usuario en el proyecto. */
    @Transactional(readOnly = true)
    public RuntimeProjectUserDetails getWithRoles(UUID projectId, UUID userId) {
        ProjectUserDetails user = get(projectId, userId);
        return RuntimeProjectUserDetails.from(user,
                effectiveAuthoritiesService.forUser(projectId, user.id()).roleKeys());
    }

    /**
     * El usuario cuyo login OIDC ({@code sub}) es {@code login}: el username si lo
     * tiene, si no el email (ver {@code ProjectUserPrincipal.loginOf}).
     */
    @Transactional(readOnly = true)
    public Optional<ProjectUserDetails> findByLogin(UUID projectId, String login) {
        String clean = login == null ? "" : login.trim();
        if (clean.isEmpty()) {
            return Optional.empty();
        }
        return repository.findByProjectIdAndUsername(projectId, clean)
                .or(() -> repository.findByProjectIdAndEmailIgnoreCase(projectId, clean)
                        .filter(user -> user.getUsername() == null))
                .map(ProjectUserDetails::from);
    }

    /** Como {@link #findByLogin(UUID, String)} incluyendo los roles en el proyecto. */
    @Transactional(readOnly = true)
    public Optional<RuntimeProjectUserDetails> findWithRolesByLogin(UUID projectId, String login) {
        return findByLogin(projectId, login)
                .map(user -> RuntimeProjectUserDetails.from(user,
                        effectiveAuthoritiesService.forUser(projectId, user.id()).roleKeys()));
    }
}
