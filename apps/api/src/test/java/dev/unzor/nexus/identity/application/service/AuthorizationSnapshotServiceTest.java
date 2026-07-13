package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.api.dto.AuthorizationSnapshot;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.permissions.application.dto.EffectiveAuthorities;
import dev.unzor.nexus.permissions.application.service.EffectiveAuthoritiesService;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests del contracto deny del snapshot (remediación #3a): un usuario
 * inexistente/eliminado (authzVersion ausente → -1) recibe un snapshot VACÍO,
 * sin permisos, y ni siquiera se computan sus authorities efectivas.
 */
class AuthorizationSnapshotServiceTest {

    private final EffectiveAuthoritiesService effectiveAuthoritiesService = mock(EffectiveAuthoritiesService.class);
    private final ProjectUserRepository projectUserRepository = mock(ProjectUserRepository.class);
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final AuthorizationSnapshotService service = new AuthorizationSnapshotService(
            effectiveAuthoritiesService, projectUserRepository, projectLookupService, Duration.ofSeconds(30));

    @Test
    void returnsEmptyDenySnapshotForNonexistentUser() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        // findAuthzVersion vacío → -1 (usuario inexistente/eliminado).
        when(projectUserRepository.findAuthzVersionByProjectIdAndId(projectId, userId))
                .thenReturn(Optional.empty());

        AuthorizationSnapshot snapshot = service.snapshot(projectId, userId);

        assertThat(snapshot.authzVersion()).isEqualTo(-1L);
        assertThat(snapshot.permissions()).isEmpty();
        assertThat(snapshot.roles()).isEmpty();
        // No se computan permisos de un usuario que no existe (sin permisos huérfanos).
        verifyNoInteractions(effectiveAuthoritiesService);
    }

    @Test
    void returnsPermissionsForExistingUser() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(projectUserRepository.findAuthzVersionByProjectIdAndId(projectId, userId))
                .thenReturn(Optional.of(3L));
        when(effectiveAuthoritiesService.forUser(projectId, userId))
                .thenReturn(new EffectiveAuthorities(new TreeSet<>(java.util.Set.of("orders.read", "orders.*"))));

        AuthorizationSnapshot snapshot = service.snapshot(projectId, userId);

        assertThat(snapshot.authzVersion()).isEqualTo(3L);
        assertThat(snapshot.permissions()).containsExactly("orders.*", "orders.read");
    }

    @Test
    void returnsDenyWhenUserIsDeletedWhileAuthoritiesAreComputed() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(projectUserRepository.findAuthzVersionByProjectIdAndId(projectId, userId))
                .thenReturn(Optional.of(3L), Optional.empty());
        when(effectiveAuthoritiesService.forUser(projectId, userId))
                .thenReturn(new EffectiveAuthorities(new TreeSet<>(java.util.Set.of("orders.read"))));

        AuthorizationSnapshot snapshot = service.snapshot(projectId, userId);

        assertThat(snapshot.authzVersion()).isEqualTo(-1L);
        assertThat(snapshot.permissions()).isEmpty();
        assertThat(snapshot.roles()).isEmpty();
    }

    @Test
    void returnsDenyWhenAuthorizationVersionChangesDuringComputation() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(projectUserRepository.findAuthzVersionByProjectIdAndId(projectId, userId))
                .thenReturn(Optional.of(3L), Optional.of(4L));
        when(effectiveAuthoritiesService.forUser(projectId, userId))
                .thenReturn(new EffectiveAuthorities(new TreeSet<>(java.util.Set.of("orders.read"))));

        AuthorizationSnapshot snapshot = service.snapshot(projectId, userId);

        assertThat(snapshot.authzVersion()).isEqualTo(-1L);
        assertThat(snapshot.permissions()).isEmpty();
    }
}
