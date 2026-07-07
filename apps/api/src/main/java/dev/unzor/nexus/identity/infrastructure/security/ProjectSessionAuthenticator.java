package dev.unzor.nexus.identity.infrastructure.security;

import dev.unzor.nexus.identity.application.configuration.IdentityLoginProperties;
import dev.unzor.nexus.identity.application.service.Base32;
import dev.unzor.nexus.identity.application.service.RecordProjectUserLoginService;
import dev.unzor.nexus.identity.application.service.TotpCrypto;
import dev.unzor.nexus.identity.application.service.TotpGenerator;
import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.domain.entity.ProjectUserRecoveryCode;
import dev.unzor.nexus.identity.domain.exception.EmailNotVerifiedException;
import dev.unzor.nexus.identity.domain.exception.MfaRequiredException;
import dev.unzor.nexus.identity.infrastructure.IdentityTokens;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRecoveryCodeRepository;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.shared.security.NexusSessionAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Autentica un {@code ProjectUser} (email + contraseña) dentro de un proyecto y
 * establece la sesión HTTP bajo la cadena {@code /p/**}. Es la pieza central del login
 * funcional y la base reutilizable por el Authorization Server por proyecto.
 *
 * <p>La autenticación es <b>manual</b> (no usa {@code DaoAuthenticationProvider}) porque
 * el email sólo es único dentro de un proyecto. Espejea el login manual del panel.</p>
 *
 * <p>Ante cualquier fallo lanza {@link BadCredentialsException} con el mismo mensaje
 * genérico (anti-enumeración). El login fallido se audita; la respuesta HTTP nunca
 * distingue la causa.</p>
 *
 * <p><b>MFA (M5):</b> si el usuario tiene TOTP activo, tras verificar la contraseña se
 * fija un ticket efímero en la sesión ({@link NexusSessionAttributes#MFA_PENDING}) y se
 * lanza {@link MfaRequiredException} — <b>sin persistir ningún
 * {@code SecurityContext}</b>, de modo que el AS no puede reanudar {@code /oauth2/authorize}.
 * El segundo factor se completa vía {@link #completeMfaAuthentication}, que verifica el
 * TOTP (o un recovery code) y entonces sí establece el contexto con ambos factores.</p>
 */
@Component
public class ProjectSessionAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(ProjectSessionAuthenticator.class);

    public static final String GENERIC_ERROR = "Invalid email or password.";
    private static final String DUMMY_PASSWORD = "nexus-timing-equalization-fixed-dummy-password";
    /** TTL del ticket MFA pendiente (ventana para introducir el código TOTP). */
    private static final Duration MFA_PENDING_TTL = Duration.ofMinutes(5);
    /** Pasos de ventana (±30s) admitidos al verificar el TOTP por sesgo de reloj. */
    private static final int TOTP_WINDOW_STEPS = 1;

    private final ProjectUserUserDetailsService userDetailsService;
    private final ProjectUserRepository repository;
    private final ProjectUserRecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final RecordProjectUserLoginService recordLoginService;
    private final ApplicationEventPublisher eventPublisher;
    private final IdentityLoginProperties loginProperties;
    private final TotpCrypto totpCrypto;
    private final String dummyPasswordHash;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public ProjectSessionAuthenticator(
            ProjectUserUserDetailsService userDetailsService,
            ProjectUserRepository repository,
            ProjectUserRecoveryCodeRepository recoveryCodeRepository,
            PasswordEncoder passwordEncoder,
            RecordProjectUserLoginService recordLoginService,
            ApplicationEventPublisher eventPublisher,
            IdentityLoginProperties loginProperties,
            TotpCrypto totpCrypto
    ) {
        this.userDetailsService = userDetailsService;
        this.repository = repository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.recordLoginService = recordLoginService;
        this.eventPublisher = eventPublisher;
        this.loginProperties = loginProperties;
        this.totpCrypto = totpCrypto;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    /**
     * Autentica email + contraseña. Si todo es correcto y NO hay MFA, establece la
     * sesión. Si hay MFA, fija el ticket pendiente y lanza {@link MfaRequiredException}
     * (sin sesión autenticada). Lanza {@link BadCredentialsException} en cualquier fallo.
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
            // Igualación de tiempos (anti-enumeración): mismo coste BCrypt que una
            // verificación real, para que un usuario inexistente no se distinga de una
            // contraseña errónea.
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            publishFailure(projectId, null, email);
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        Instant now = Instant.now();
        if (user.isLocked(now)) {
            publishFailure(projectId, user.getId(), user.getEmail());
            throw new BadCredentialsException(GENERIC_ERROR);
        }
        if (!passwordEncoder.matches(rawPassword, principal.getPassword())) {
            recordFailedLoginBestEffort(user, now);
            publishFailure(projectId, user.getId(), user.getEmail());
            throw new BadCredentialsException(GENERIC_ERROR);
        }
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException(projectId, user.getEmail());
        }
        if (!user.canAuthenticate()) {
            publishFailure(projectId, user.getId(), user.getEmail());
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        // Identidad confirmada. Si MFA activa → ticket pendiente + excepción (NO se
        // persiste SecurityContext; la sesión sigue anónima para SAS).
        if (user.isMfaEnabled()) {
            HttpSession session = request.getSession();
            session.setAttribute(NexusSessionAttributes.MFA_PENDING,
                    new MfaPendingTicket(user.getId(), projectId, now, now.plus(MFA_PENDING_TTL)));
            throw new MfaRequiredException(projectId, user.getEmail());
        }

        return establishSession(principal, user, projectId, now, null, request, response);
    }

    /**
     * Completa el login MFA: lee el ticket pendiente de la sesión, verifica el código
     * TOTP (o un recovery code) y, si es válido, establece la sesión con ambos factores.
     * Lanza {@link BadCredentialsException} si no hay ticket, está expirado, o el código
     * no valida (esto último cuenta hacia el lockout).
     */
    public Authentication completeMfaAuthentication(
            UUID projectId, String code, HttpServletRequest request, HttpServletResponse response
    ) {
        Instant now = Instant.now();
        HttpSession session = request.getSession(false);
        Object raw = (session == null) ? null : session.getAttribute(NexusSessionAttributes.MFA_PENDING);
        if (!(raw instanceof MfaPendingTicket ticket)
                || !Objects.equals(ticket.projectId(), projectId)
                || ticket.expiresAt().isBefore(now)) {
            // Sin ticket MFA válido: genérico (no revela nada).
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        ProjectUser user = repository.findByProjectIdAndId(projectId, ticket.userId())
                .orElseThrow(() -> new BadCredentialsException(GENERIC_ERROR));
        if (user.isLocked(now)) {
            publishMfaFailure(projectId, user);
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        if (!verifyTotpOrRecovery(user, code, now)) {
            recordFailedLoginBestEffort(user, now);
            publishMfaFailure(projectId, user);
            throw new BadCredentialsException(GENERIC_ERROR);
        }

        // Éxito: anular el ticket y establecer la sesión con ambos factores. Rotar el id
        // de sesión (anti session-fixation) — el ticket se quitó antes para que no migre.
        session.removeAttribute(NexusSessionAttributes.MFA_PENDING);
        ProjectUserPrincipal principal =
                (ProjectUserPrincipal) userDetailsService.loadProjectUser(projectId, user.getEmail());
        return establishSession(principal, user, projectId, ticket.passwordVerifiedAt(), now, request, response);
    }

    /**
     * Verifica el segundo factor: un código TOTP de 6 dígitos (contra el secret
     * descifrado) o, en su defecto, un recovery code single-use (hash SHA-256). El
     * recovery consumido se marca al usarlo.
     */
    private boolean verifyTotpOrRecovery(ProjectUser user, String code, Instant now) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String trimmed = code.trim();
        // TOTP: 6 dígitos contra el secret descifrado.
        if (user.getTotpSecretEnc() != null && trimmed.length() == TotpGenerator.DIGITS) {
            try {
                byte[] secret = Base32.decode(totpCrypto.decrypt(user.getTotpSecretEnc()));
                if (TotpGenerator.verify(secret, trimmed, now.getEpochSecond(), TOTP_WINDOW_STEPS)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // secret corrupto: caer a recovery (no revelar)
            }
        }
        // Recovery code: hash SHA-256, single-use.
        Optional<ProjectUserRecoveryCode> match =
                recoveryCodeRepository.findByCodeHashAndConsumedAtIsNull(IdentityTokens.hash(trimmed));
        if (match.isPresent()) {
            match.get().consume(now);
            recoveryCodeRepository.save(match.get());
            return true;
        }
        return false;
    }

    /**
     * Mitad de éxito compartida: limpia intentos fallidos, rota el id de sesión
     * (anti session-fixation), indexa por {@code PROJECT_USER_ID}, construye el
     * {@code Authentication} con el factor PASSWORD (y opcionalmente TOTP), persiste el
     * {@code SecurityContext} y registra/audita el login.
     *
     * @param totpVerifiedAt instante de verificación TOTP; {@code null} si no hay MFA.
     */
    private Authentication establishSession(
            ProjectUserPrincipal principal, ProjectUser user, UUID projectId,
            Instant passwordVerifiedAt, Instant totpVerifiedAt,
            HttpServletRequest request, HttpServletResponse response
    ) {
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.resetFailedLogins();
            saveBestEffort(user);
        }

        request.getSession();
        request.changeSessionId();
        request.getSession().setAttribute(
                NexusSessionAttributes.PROJECT_USER_ID, user.getId().toString());

        ProjectUserPrincipal sessionPrincipal = principal.withoutCredentials();
        // Spring Security 7.0 deriva auth_time del id_token a partir del issuedAt de un
        // FactorGrantedAuthority. El TOTP factor (si lo hay) refleja el segundo factor.
        List<GrantedAuthority> authorities = new ArrayList<>(sessionPrincipal.getAuthorities());
        authorities.add(FactorGrantedAuthority
                .withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY).issuedAt(passwordVerifiedAt).build());
        if (totpVerifiedAt != null) {
            authorities.add(FactorGrantedAuthority.withFactor("TOTP").issuedAt(totpVerifiedAt).build());
        }
        Authentication authentication = new UsernamePasswordAuthenticationToken(sessionPrincipal, null, authorities);

        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        securityContextRepository.saveContext(securityContext, request, response);

        recordLoginService.recordLogin(projectId, user.getId());
        eventPublisher.publishEvent(AuditEvent.byProjectUser(
                projectId, "project_user.login_succeeded", "project_user",
                Objects.toString(user.getId(), null), user.getId(), Map.of(
                        "email", user.getEmail(),
                        "mfa", String.valueOf(totpVerifiedAt != null))));

        return authentication;
    }

    private void publishFailure(UUID projectId, UUID userId, String email) {
        eventPublisher.publishEvent(AuditEvent.byProjectUser(
                projectId, "project_user.login_failed", "project_user",
                userId == null ? null : userId.toString(), userId, Map.of("email", email)));
    }

    private void publishMfaFailure(UUID projectId, ProjectUser user) {
        eventPublisher.publishEvent(AuditEvent.byProjectUser(
                projectId, "project_user.mfa_failed", "project_user",
                Objects.toString(user.getId(), null), user.getId(), Map.of("email", user.getEmail())));
    }

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
