package dev.unzor.nexus.identity.infrastructure.security;

import dev.unzor.nexus.identity.application.service.RecordProjectUserLoginService;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Autentica un {@code ProjectUser} (email + contraseña) dentro de un proyecto y
 * establece la sesión HTTP bajo la cadena {@code /p/**}. Es la pieza central del
 * login funcional de B1 y la base reutilizable por el Authorization Server por
 * proyecto (B2).
 *
 * <p>La autenticación es <b>manual</b> (no usa {@code DaoAuthenticationProvider})
 * porque el email sólo es único dentro de un proyecto: hay que resolver primero
 * el {@code projectId} desde el slug y pasárselo al
 * {@link ProjectUserUserDetailsService}. Espejea el login manual del panel
 * ({@code PanelSessionController}).</p>
 *
 * <p>Ante cualquier fallo (usuario inexistente, suspendido/desactivado, contraseña
 * errónea) lanza {@link BadCredentialsException} con el <i>mismo</i> mensaje
 * genérico, para no revelar si el email existe (anti-enumeración). El login
 * fallido se audita; la respuesta HTTP nunca distingue la causa.</p>
 */
@Component
public class ProjectSessionAuthenticator {

    public static final String GENERIC_ERROR = "Invalid email or password.";

    private final ProjectUserUserDetailsService userDetailsService;
    private final ProjectUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final RecordProjectUserLoginService recordLoginService;
    private final ApplicationEventPublisher eventPublisher;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public ProjectSessionAuthenticator(
            ProjectUserUserDetailsService userDetailsService,
            ProjectUserRepository repository,
            PasswordEncoder passwordEncoder,
            RecordProjectUserLoginService recordLoginService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.userDetailsService = userDetailsService;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.recordLoginService = recordLoginService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Autentica y, si todo es correcto, rota el id de sesión (anti session
     * fixation), persiste el {@code SecurityContext} en la sesión con un
     * principal sin credenciales, registra el login y devuelve el
     * {@link Authentication}. Lanza {@link BadCredentialsException} si no.
     */
    public Authentication authenticate(
            UUID projectId, String email, String rawPassword,
            HttpServletRequest request, HttpServletResponse response
    ) {
        ProjectUserPrincipal principal;
        ProjectUser user;
        try {
            UserDetails userDetails = userDetailsService.loadProjectUser(projectId, email);
            principal = (ProjectUserPrincipal) userDetails;
            user = repository.findByProjectIdAndId(projectId, principal.userId())
                    .orElseThrow(() -> new UsernameNotFoundException("Project user not found"));
        } catch (UsernameNotFoundException e) {
            publishFailure(projectId, null, email);
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        if (!user.canAuthenticate()) {
            publishFailure(projectId, user.getId(), user.getEmail());
            throw new BadCredentialsException(GENERIC_ERROR);
        }
        if (!passwordEncoder.matches(rawPassword, principal.getPassword())) {
            publishFailure(projectId, user.getId(), user.getEmail());
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        // Éxito: anti session-fixation (rotar el id de sesión, igual que el panel).
        request.getSession();
        request.changeSessionId();

        ProjectUserPrincipal sessionPrincipal = principal.withoutCredentials();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                sessionPrincipal, null, sessionPrincipal.getAuthorities());

        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        securityContextRepository.saveContext(securityContext, request, response);

        recordLoginService.recordLogin(projectId, user.getId());
        eventPublisher.publishEvent(AuditEvent.byProjectUser(
                projectId, "project_user.login_succeeded", "project_user",
                Objects.toString(user.getId(), null), user.getId(), Map.of("email", user.getEmail())));

        return authentication;
    }

    private void publishFailure(UUID projectId, UUID userId, String email) {
        eventPublisher.publishEvent(AuditEvent.byProjectUser(
                projectId, "project_user.login_failed", "project_user",
                userId == null ? null : userId.toString(), userId, Map.of("email", email)));
    }
}
