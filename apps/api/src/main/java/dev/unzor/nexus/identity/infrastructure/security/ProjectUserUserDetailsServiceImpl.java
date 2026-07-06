package dev.unzor.nexus.identity.infrastructure.security;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.permissions.application.dto.EffectiveAuthorities;
import dev.unzor.nexus.permissions.application.service.EffectiveAuthoritiesService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementación canónica de {@link ProjectUserUserDetailsService}. Carga un
 * {@link ProjectUser} por {@code (projectId, email)} y lo adapta a
 * {@link ProjectUserPrincipal}. Es la base reutilizable por el login (B1) y por
 * el Authorization Server por proyecto (B2): nunca se registra como
 * {@code UserDetailsService} global y siempre incluye el {@code projectId}.
 *
 * <p>Las authorities son {@code ROLE_PROJECT_USER} (base incondicional) más una
 * authority por cada clave de permiso efectiva del usuario (unión de las de sus
 * roles), resuelta vía {@link EffectiveAuthoritiesService} del módulo
 * {@code permissions}.</p>
 */
@Service
public class ProjectUserUserDetailsServiceImpl implements ProjectUserUserDetailsService {

    /** Authority base, siempre presente para todo ProjectUser autenticado. */
    private static final GrantedAuthority BASE_AUTHORITY = new SimpleGrantedAuthority("ROLE_PROJECT_USER");

    private final ProjectUserRepository repository;
    private final EffectiveAuthoritiesService effectiveAuthoritiesService;

    public ProjectUserUserDetailsServiceImpl(
            ProjectUserRepository repository,
            EffectiveAuthoritiesService effectiveAuthoritiesService
    ) {
        this.repository = repository;
        this.effectiveAuthoritiesService = effectiveAuthoritiesService;
    }

    @Override
    public UserDetails loadProjectUser(UUID projectId, String login) throws UsernameNotFoundException {
        ProjectUser user = repository.findByProjectIdAndEmailIgnoreCase(projectId, login)
                .orElseThrow(() -> new UsernameNotFoundException("Project user not found"));
        return ProjectUserPrincipal.from(user, authoritiesFor(projectId, user.getId()));
    }

    /**
     * {@code ROLE_PROJECT_USER} (base incondicional) + una authority por cada
     * clave de permiso efectiva del usuario (unión de las de sus roles). Los
     * comodines ({@code orders.*}, {@code *}) se incluyen tal cual; su expansión
     * a claves concretas queda fuera del alcance actual.
     */
    private List<GrantedAuthority> authoritiesFor(UUID projectId, UUID userId) {
        EffectiveAuthorities effective = effectiveAuthoritiesService.forUser(projectId, userId);
        List<GrantedAuthority> authorities = new ArrayList<>(effective.permissionKeys().size() + 1);
        authorities.add(BASE_AUTHORITY);
        effective.permissionKeys().forEach(key -> authorities.add(new SimpleGrantedAuthority(key)));
        return authorities;
    }
}
