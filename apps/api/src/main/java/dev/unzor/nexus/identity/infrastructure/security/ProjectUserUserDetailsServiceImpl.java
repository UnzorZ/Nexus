package dev.unzor.nexus.identity.infrastructure.security;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Implementación canónica de {@link ProjectUserUserDetailsService}. Carga un
 * {@link ProjectUser} por {@code (projectId, email)} y lo adapta a
 * {@link ProjectUserPrincipal}. Es la base reutilizable por el login (B1) y por
 * el Authorization Server por proyecto (B2): nunca se registra como
 * {@code UserDetailsService} global y siempre incluye el {@code projectId}.
 *
 * <p>En B1 las authorities son un único rol {@code ROLE_PROJECT_USER}; la
 * resolución de permisos/roles efectivos llega con la integración del módulo
 * {@code permissions}.</p>
 */
@Service
public class ProjectUserUserDetailsServiceImpl implements ProjectUserUserDetailsService {

    private static final List<GrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_PROJECT_USER"));

    private final ProjectUserRepository repository;

    public ProjectUserUserDetailsServiceImpl(ProjectUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadProjectUser(UUID projectId, String login) throws UsernameNotFoundException {
        ProjectUser user = repository.findByProjectIdAndEmailIgnoreCase(projectId, login)
                .orElseThrow(() -> new UsernameNotFoundException("Project user not found"));
        return ProjectUserPrincipal.from(user, AUTHORITIES);
    }
}
