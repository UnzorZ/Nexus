package dev.unzor.nexus.admin.infrastructure.security;

import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
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
 * <p>Implementa {@link Serializable} porque Spring Session Redis serializa la sesión
 * (incluido el {@code SecurityContext} que referencia al principal) mediante
 * serialización JDK. Implementa además {@link CredentialsContainer} para que
 * {@link #eraseCredentials()} elimine el hash de la contraseña: nunca se conserva el
 * hash dentro del principal serializado que se almacena en Redis. El
 * {@code PanelAuthenticationSuccessHandler} invoca {@code eraseCredentials()} tras un
 * login correcto, antes de que la sesión se persista. Nunca se persisten entidades JPA
 * en sesión.</p>
 *
 * <p>Es una clase (no un {@code record}) porque {@code eraseCredentials()} debe poder
 * mutar la contraseña a {@code null}.</p>
 */
public final class NexusAccountPrincipal implements UserDetails, CredentialsContainer, Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID accountId;
    private final String username;
    private transient String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;

    public NexusAccountPrincipal(
            UUID accountId,
            String username,
            String password,
            Collection<? extends GrantedAuthority> authorities,
            boolean enabled
    ) {
        this.accountId = accountId;
        this.username = username;
        this.password = password;
        this.authorities = List.copyOf(authorities);
        this.enabled = enabled;
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

    /** Identificador estable de la cuenta autenticada. */
    public UUID accountId() {
        return accountId;
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

    /**
     * Elimina la contraseña del principal. Spring Security invoca este método en los
     * puntos habituales (p. ej. tras ciertas autenticaciones); el
     * {@code PanelAuthenticationSuccessHandler} también lo invoca de forma explícita
     * antes de persistir la sesión en Redis, de modo que el hash nunca se serializa en
     * el {@code SecurityContext} almacenado.
     */
    @Override
    public void eraseCredentials() {
        password = null;
    }
}
