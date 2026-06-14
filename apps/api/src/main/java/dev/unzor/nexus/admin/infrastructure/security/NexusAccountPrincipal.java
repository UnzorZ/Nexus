package dev.unzor.nexus.admin.infrastructure.security;

import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Representación de una {@link NexusAccount} utilizada por Spring Security.
 *
 * <p>Mantiene el contrato {@link UserDetails} fuera de la entidad de dominio. Las
 * authorities se calculan al cargar la cuenta a partir de sus grants globales y del
 * contexto de autorización aplicable; no forman parte de {@code NexusAccount}.</p>
 *
 * @param accountId identificador estable de la cuenta autenticada
 * @param username email utilizado para el login del panel
 * @param password hash de la contraseña almacenada
 * @param authorities permisos y roles efectivos para la autenticación
 * @param enabled si el estado de la cuenta permite autenticarse
 */
public record NexusAccountPrincipal(
        UUID accountId,
        String username,
        String password,
        Collection<? extends GrantedAuthority> authorities,
        boolean enabled
) implements UserDetails {

    /**
     * Crea una copia inmutable de las authorities para evitar cambios durante la sesión.
     */
    public NexusAccountPrincipal {
        authorities = List.copyOf(authorities);
    }

    public static NexusAccountPrincipal from(
            NexusAccount account,
            Collection<? extends GrantedAuthority> authorities
    ) {
        return new NexusAccountPrincipal(
                account.getId(),
                account.getEmail(),
                account.getPasswordHash(),
                authorities,
                account.canAuthenticate()
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
