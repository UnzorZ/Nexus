package dev.unzor.nexus.identity.infrastructure.security;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.permissions.application.dto.EffectiveAuthorities;
import dev.unzor.nexus.permissions.application.service.EffectiveAuthoritiesService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectUserUserDetailsServiceImplTest {

    private final ProjectUserRepository repository = mock(ProjectUserRepository.class);
    private final EffectiveAuthoritiesService effectiveAuthorities = mock(EffectiveAuthoritiesService.class);
    private final ProjectUserUserDetailsServiceImpl service =
            new ProjectUserUserDetailsServiceImpl(repository, effectiveAuthorities);

    @Test
    void authoritiesAreBaselinePlusRoleDerivedKeys() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProjectUser user = new ProjectUser(projectId, "alice@example.com", "hash", "Alice");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        when(repository.findByProjectIdAndEmailIgnoreCase(projectId, "alice@example.com"))
                .thenReturn(Optional.of(user));
        when(effectiveAuthorities.forUser(projectId, userId)).thenReturn(new EffectiveAuthorities(
                new TreeSet<>(java.util.Set.of("orders.read", "orders.*"))));

        ProjectUserPrincipal principal = (ProjectUserPrincipal) service.loadProjectUser(projectId, "alice@example.com");

        assertThat(principal.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactlyInAnyOrder("ROLE_PROJECT_USER", "orders.*", "orders.read");
        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.getUsername()).isEqualTo("alice@example.com");
    }

    @Test
    void userWithNoRolesGetsBaselineAuthorityOnly() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProjectUser user = new ProjectUser(projectId, "neo@example.com", "hash", "Neo");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        when(repository.findByProjectIdAndEmailIgnoreCase(projectId, "neo@example.com"))
                .thenReturn(Optional.of(user));
        when(effectiveAuthorities.forUser(projectId, userId)).thenReturn(EffectiveAuthorities.empty());

        ProjectUserPrincipal principal = (ProjectUserPrincipal) service.loadProjectUser(projectId, "neo@example.com");

        assertThat(principal.getAuthorities()).extracting(a -> a.getAuthority()).containsExactly("ROLE_PROJECT_USER");
    }
}
