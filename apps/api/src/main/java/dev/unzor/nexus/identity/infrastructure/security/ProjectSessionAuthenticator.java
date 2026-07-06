package dev.unzor.nexus.identity.infrastructure.security;

import dev.unzor.nexus.identity.application.configuration.IdentityLoginProperties;
import dev.unzor.nexus.identity.application.service.RecordProjectUserLoginService;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.shared.security.NexusSessionAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    private static final Logger log = LoggerFactory.getLogger(ProjectSessionAuthenticator.class);

    public static final String GENERIC_ERROR = "Invalid email or password.";

    /** Contraseña fija cuyo hash precalculado iguala el tiempo de la rama "usuario inexistente". */
    private static final String DUMMY_PASSWORD = "nexus-timing-equalization-fixed-dummy-password";

    private final ProjectUserUserDetailsService userDetailsService;
    private final ProjectUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final RecordProjectUserLoginService recordLoginService;
    private final ApplicationEventPublisher eventPublisher;
    private final IdentityLoginProperties loginProperties;
    /**
     * Hash precalculado al construir el componente para que la rama de "usuario
     * inexistente" ejecute exactamente un {@code matches} de BCrypt, igualando su
     * tiempo al de "contraseña errónea" (anti-enumeración por timing, B3).
     */
    private final String dummyPasswordHash;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public ProjectSessionAuthenticator(
            ProjectUserUserDetailsService userDetailsService,
            ProjectUserRepository repository,
            PasswordEncoder passwordEncoder,
            RecordProjectUserLoginService recordLoginService,
            ApplicationEventPublisher eventPublisher,
            IdentityLoginProperties loginProperties
    ) {
        this.userDetailsService = userDetailsService;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.recordLoginService = recordLoginService;
        this.eventPublisher = eventPublisher;
        this.loginProperties = loginProperties;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
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
            // Igualación de tiempos (B3): ejecutar el mismo coste BCrypt que en una
            // verificación real, para que un usuario inexistente no se distinga de una
            // contraseña errónea por el tiempo de respuesta (anti-enumeración).
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            publishFailure(projectId, null, email);
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        Instant now = Instant.now();
        if (!user.canAuthenticate()) {
            publishFailure(projectId, user.getId(), user.getEmail());
            throw new BadCredentialsException(GENERIC_ERROR);
        }
        if (user.isLocked(now)) {
            // Bloqueo temporal por intentos fallidos: mismo mensaje genérico para no
            // revelar que la cuenta existe ni que está bloqueada (anti-enumeración).
            publishFailure(projectId, user.getId(), user.getEmail());
            throw new BadCredentialsException(GENERIC_ERROR);
        }
        if (!passwordEncoder.matches(rawPassword, principal.getPassword())) {
            recordFailedLoginBestEffort(user, now);
            publishFailure(projectId, user.getId(), user.getEmail());
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        // Éxito: limpiar el contador de intentos fallidos (best-effort, sólo si procede).
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.resetFailedLogins();
            saveBestEffort(user);
        }

        // Éxito: anti session-fixation (rotar el id de sesión, igual que el panel).
        request.getSession();
        request.changeSessionId();
        // Indexa la sesión por el id del usuario de proyecto para poder revocarla
        // (suspend/disable/delete) vía Redis, igual que las del panel.
        request.getSession().setAttribute(
                NexusSessionAttributes.PROJECT_USER_ID, user.getId().toString());

        ProjectUserPrincipal sessionPrincipal = principal.withoutCredentials();
        // Spring Security 7.0 deriva el claim auth_time del id_token a partir del
        // issuedAt de un FactorGrantedAuthority del Authentication. El
        // DaoAuthenticationProvider estándar lo añade automáticamente; como ésta es
        // autenticación manual hay que incluirlo, si no SAS lanza
        // "authenticationTime cannot be null" al emitir el id_token (flujo
        // authorization_code + scope openid). Round-tripa por JDBC gracias al
        // FactorGrantedAuthorityMixin de SecurityJacksonModules.
        GrantedAuthority passwordFactor = FactorGrantedAuthority
                .withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY).issuedAt(now).build();
        List<GrantedAuthority> authorities = new ArrayList<>(sessionPrincipal.getAuthorities());
        authorities.add(passwordFactor);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                sessionPrincipal, null, authorities);

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

    /**
     * Registra un intento fallido e incrementa/bloquea (best-effort): un fallo de
     * persistencia no debe impedir el rechazo del login; el contador queda como esté.
     */
    private void recordFailedLoginBestEffort(ProjectUser user, Instant now) {
        try {
            user.recordFailedLogin(now, loginProperties.maxAttempts(), loginProperties.lockoutDuration());
            repository.save(user);
        } catch (RuntimeException e) {
            log.warn("Failed to record project-user failed login: userId={}", user.getId(), e);
        }
    }

    private void saveBestEffort(ProjectUser user) {
        try {
            repository.save(user);
        } catch (RuntimeException e) {
            log.warn("Failed to persist project-user state: userId={}", user.getId(), e);
        }
    }
}
