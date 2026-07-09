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
        EffectiveAuthorities authorities = effectiveAuthoritiesService.forUser(projectId, userId);
        long authzVersion = projectUserRepository.findAuthzVersionByProjectIdAndId(projectId, userId)
                .orElse(-1L);
        return new AuthorizationSnapshot(
                userId,
                projectId,
                authzVersion,
                List.copyOf(authorities.roleKeys()),
                List.copyOf(authorities.permissionKeys()),
                Instant.now().plus(snapshotTtl));
    }
}
