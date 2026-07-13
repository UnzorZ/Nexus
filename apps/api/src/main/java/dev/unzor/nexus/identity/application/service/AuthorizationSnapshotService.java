package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.api.dto.AuthorizationSnapshot;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.permissions.application.dto.EffectiveAuthorities;
import dev.unzor.nexus.permissions.application.service.EffectiveAuthoritiesService;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Construye el snapshot de autorización de un usuario de proyecto (spec §14.11):
 * permisos efectivos + roles + {@code authzVersion} vigente, con un
 * {@code expiresAt} = ahora + TTL configurado. Combina
 * {@link EffectiveAuthoritiesService} (permisos+roles, módulo {@code permissions})
 * con {@link ProjectUserRepository} ({@code authzVersion}, módulo {@code identity}).
 *
 * <p>Para un usuario inexistente devuelve un snapshot "deny": permisos vacíos,
 * {@code authzVersion = -1}. El SDK debe tratarlo como denegación (fail-safe).</p>
 */
@Service
public class AuthorizationSnapshotService {

    private final EffectiveAuthoritiesService effectiveAuthoritiesService;
    private final ProjectUserRepository projectUserRepository;
    private final ProjectLookupService projectLookupService;
    private final Duration snapshotTtl;

    public AuthorizationSnapshotService(
            EffectiveAuthoritiesService effectiveAuthoritiesService,
            ProjectUserRepository projectUserRepository,
            ProjectLookupService projectLookupService,
            @Value("${nexus.authz.snapshot.ttl:30s}") Duration snapshotTtl
    ) {
        this.effectiveAuthoritiesService = effectiveAuthoritiesService;
        this.projectUserRepository = projectUserRepository;
        this.projectLookupService = projectLookupService;
        this.snapshotTtl = snapshotTtl;
    }

    @Transactional(readOnly = true)
    public AuthorizationSnapshot snapshot(UUID projectId, UUID userId) {
        projectLookupService.requireById(projectId);
        Optional<Long> initialVersion = projectUserRepository.findAuthzVersionByProjectIdAndId(projectId, userId);
        // Usuario inexistente / eliminado: snapshot "deny" — permisos VACÍOS y
        // authzVersion = -1 (remediación de auditoría). Antes se devolvían los
        // permisos efectivos (posiblemente huérfanos de project_user_roles) junto a
        // authzVersion = -1, y el SDK no comprobaba la versión → concedía acceso a
        // usuarios inexistentes/eliminados. Con permisos vacíos no hay nada que
        // conceder, y authzVersion negativa es denegación explícita para el SDK.
        if (initialVersion.isEmpty()) {
            return denySnapshot(projectId, userId);
        }
        EffectiveAuthorities authorities = effectiveAuthoritiesService.forUser(projectId, userId);
        Optional<Long> confirmedVersion = projectUserRepository.findAuthzVersionByProjectIdAndId(projectId, userId);
        // READ COMMITTED da un snapshot nuevo por sentencia: si el usuario desapareció
        // o su versión cambió mientras se resolvían roles/permisos, nunca publicamos el
        // resultado potencialmente huérfano/stale. El cliente podrá reintentar y hasta
        // entonces recibe denegación explícita.
        if (!confirmedVersion.equals(initialVersion)) {
            return denySnapshot(projectId, userId);
        }
        return new AuthorizationSnapshot(
                userId,
                projectId,
                confirmedVersion.orElseThrow(),
                List.copyOf(authorities.roleKeys()),
                List.copyOf(authorities.permissionKeys()),
                Instant.now().plus(snapshotTtl));
    }

    private AuthorizationSnapshot denySnapshot(UUID projectId, UUID userId) {
        return new AuthorizationSnapshot(userId, projectId, -1L, List.of(), List.of(),
                Instant.now().plus(snapshotTtl));
    }
}
