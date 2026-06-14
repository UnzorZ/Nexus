package dev.unzor.nexus.identity.infrastructure.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.UUID;

/**
 * Contrato para cargar {@link UserDetails} de un {@code ProjectUser} dentro de un
 * proyecto concreto. No debe registrarse como {@code UserDetailsService} global.
 */
public interface ProjectUserUserDetailsService {

    UserDetails loadProjectUser(UUID projectId, String login) throws UsernameNotFoundException;
}
