package dev.unzor.nexus.identity.infrastructure.security;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Representación de un {@link ProjectUser} utilizada por Spring Security.
 *
 * <p>Incluye siempre {@code projectId} para conservar el aislamiento del realm
 * durante la autenticación. El servicio que construya este principal debe resolver
 * primero el proyecto desde el issuer o la ruta OAuth y nunca buscar usuarios
 * globalmente solo por email o username.</p>
 *
 * @param projectId proyecto al que pertenece la identidad autenticada
 * @param userId identificador del usuario dentro de Nexus
 * @param username username del proyecto o, si no existe, email del usuario
 * @param password hash de la contraseña almacenada
 * @param authorities permisos y roles efectivos dentro del proyecto
 * @param enabled si el estado del usuario permite autenticarse
 */
public record ProjectUserPrincipal(
        UUID projectId,
        UUID userId,
        String username,
        String password,
        Collection<? extends GrantedAuthority> authorities,
        boolean enabled
) implements UserDetails {

    /**
     * Crea una copia inmutable de las authorities para evitar cambios durante la sesión.
     */
    public ProjectUserPrincipal {
        authorities = List.copyOf(authorities);
    }

    public static ProjectUserPrincipal from(
            ProjectUser user,
            Collection<? extends GrantedAuthority> authorities
    ) {
        String login = user.getUsername() == null ? user.getEmail() : user.getUsername();

        return new ProjectUserPrincipal(
                user.getProjectId(),
                user.getId(),
                login,
                user.getPasswordHash(),
                authorities,
                user.canAuthenticate()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
