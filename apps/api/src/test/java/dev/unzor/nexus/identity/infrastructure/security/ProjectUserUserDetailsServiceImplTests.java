package dev.unzor.nexus.identity.infrastructure.security;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.permissions.application.dto.EffectiveAuthorities;
import dev.unzor.nexus.permissions.application.service.EffectiveAuthoritiesService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectUserUserDetailsServiceImplTests {

    private final ProjectUserRepository repository = mock(ProjectUserRepository.class);
    private final EffectiveAuthoritiesService effectiveAuthorities = mock(EffectiveAuthoritiesService.class);
    private final ProjectUserUserDetailsServiceImpl service =
            new ProjectUserUserDetailsServiceImpl(repository, effectiveAuthorities);

    @Test
    void loadsUserScopedByProjectAndGrantsProjectUserRole() {
        UUID projectId = UUID.randomUUID();
        ProjectUser user = activeUser(projectId, "neo@example.com");
        when(repository.findByProjectIdAndEmailIgnoreCase(projectId, "neo@example.com"))
                .thenReturn(Optional.of(user));
        when(effectiveAuthorities.forUser(any(), any())).thenReturn(EffectiveAuthorities.empty());

        var principal = (ProjectUserPrincipal) service.loadProjectUser(projectId, "neo@example.com");

        assertThat(principal.userId()).isEqualTo(user.getId());
        assertThat(principal.projectId()).isEqualTo(projectId);
        // Sin roles asignados → sólo la authority base.
        assertThat(principal.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_PROJECT_USER");
    }

    @Test
    void unknownUserThrowsUsernameNotFound() {
        UUID projectId = UUID.randomUUID();
        when(repository.findByProjectIdAndEmailIgnoreCase(projectId, "ghost@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadProjectUser(projectId, "ghost@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void queryAlwaysIncludesProjectContext() {
        UUID projectId = UUID.randomUUID();
        when(repository.findByProjectIdAndEmailIgnoreCase(projectId, "neo@example.com"))
                .thenReturn(Optional.of(activeUser(projectId, "neo@example.com")));
        when(effectiveAuthorities.forUser(any(), any())).thenReturn(EffectiveAuthorities.empty());

        service.loadProjectUser(projectId, "neo@example.com");

        // El aislamiento entre realms depende de que NUNCA se busque sin projectId.
        verify(repository).findByProjectIdAndEmailIgnoreCase(projectId, "neo@example.com");
    }

    private static ProjectUser activeUser(UUID projectId, String email) {
        ProjectUser user = new ProjectUser(projectId, email, "$2a$10$hash", "Neo");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.verifyEmail(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }
}
