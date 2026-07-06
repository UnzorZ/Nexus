package dev.unzor.nexus.permissions.application.service;

import dev.unzor.nexus.TestcontainersConfiguration;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserUserDetailsService;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.permissions.domain.entity.ProjectRole;
import dev.unzor.nexus.permissions.persistence.repository.ProjectRoleRepository;
import dev.unzor.nexus.projects.domain.entity.Project;
import dev.unzor.nexus.projects.persistence.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT end-to-end del módulo de asignación usuario↔rol: valida la cadena completa
 * (asignación → evento compartido → listener que bumpa {@code authz_version} →
 * bump transitivo al cambiar permisos del rol → CASCADE al borrar el rol →
 * resolución de authorities en el UserDetailsService). Cubre el wiring
 * cross-module que los tests unitarios mockean.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProjectUserRoleAssignmentIntegrationTests {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectUserRepository projectUserRepository;
    @Autowired private ProjectRoleRepository roleRepository;
    @Autowired private ProjectUserRolesService userRolesService;
    @Autowired private ProjectRolesService rolesService;
    @Autowired private ProjectUserUserDetailsService userDetailsService;

    @Test
    void assignBumpsAuthzVersionTransitivelyResolvesAuthoritiesAndCascadesOnDelete() {
        Project project = projectRepository.save(
                new Project("it-ura-" + UUID.randomUUID(), "IT", null, null));
        UUID projectId = project.getId();

        ProjectUser user = new ProjectUser(projectId, "it-ura@example.com", "hash", "IT User");
        projectUserRepository.save(user);
        UUID userId = user.getId();

        ProjectRole role = roleRepository.save(new ProjectRole(projectId, "admin", "Admin", null));
        UUID roleId = role.getId();
        // Seed the role with a permission (no assignee yet → no transitive bump).
        rolesService.setPermissions(projectId, roleId, List.of("orders.read", "orders.*"), UUID.randomUUID());

        assertThat(projectUserRepository.findByProjectIdAndId(projectId, userId).orElseThrow().getAuthzVersion())
                .isZero();

        // Assign → authz_version 0→1.
        userRolesService.setRoles(projectId, userId, List.of(roleId), UUID.randomUUID());
        assertThat(projectUserRepository.findByProjectIdAndId(projectId, userId).orElseThrow().getAuthzVersion())
                .isEqualTo(1L);

        // Transitive: changing the role's permissions bumps every assignee → 1→2.
        rolesService.setPermissions(projectId, roleId, List.of("orders.write"), UUID.randomUUID());
        assertThat(projectUserRepository.findByProjectIdAndId(projectId, userId).orElseThrow().getAuthzVersion())
                .isEqualTo(2L);

        // The UserDetailsService now resolves the role's permission as an authority.
        ProjectUserPrincipal principal =
                (ProjectUserPrincipal) userDetailsService.loadProjectUser(projectId, "it-ura@example.com");
        assertThat(principal.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactlyInAnyOrder("ROLE_PROJECT_USER", "orders.write");

        // Delete the role → CASCADE removes the assignment + transitive bump → 2→3.
        rolesService.delete(projectId, roleId, UUID.randomUUID());
        assertThat(projectUserRepository.findByProjectIdAndId(projectId, userId).orElseThrow().getAuthzVersion())
                .isEqualTo(3L);
        assertThat(userRolesService.rolesForUser(projectId, userId)).isEmpty();
    }
}
