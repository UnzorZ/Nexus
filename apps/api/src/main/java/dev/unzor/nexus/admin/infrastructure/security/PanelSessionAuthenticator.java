package dev.unzor.nexus.admin.infrastructure.security;

import dev.unzor.nexus.admin.application.configuration.PanelSessionConfiguration;
import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import dev.unzor.nexus.admin.domain.entity.NexusAccountRecoveryCode;
import dev.unzor.nexus.admin.domain.exception.MfaRequiredException;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRecoveryCodeRepository;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import dev.unzor.nexus.shared.security.Base32;
import dev.unzor.nexus.shared.security.NexusSessionAttributes;
import dev.unzor.nexus.shared.security.SecureHashes;
import dev.unzor.nexus.shared.security.TotpCrypto;
import dev.unzor.nexus.shared.security.TotpGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Autentica una cuenta Nexus (email + contraseña) para el panel e inserta el paso MFA
 * TOTP cuando la cuenta lo tiene activo. Espejo de {@code ProjectSessionAuthenticator}
 * adaptado al panel: reutiliza el {@link AuthenticationManager} del panel
 * ({@code DaoAuthenticationProvider}) para verificar la contraseña y, si la cuenta tiene
 * MFA, fija un ticket efímero en la sesión y lanza {@link MfaRequiredException}
 * <b>sin persistir ningún {@code SecurityContext}</b> — la sesión sigue anónima hasta que
 * {@code POST /api/panel/v1/session/login/mfa} verifica el TOTP.
 *
 * <p>Cuando no hay MFA (o tras completarla), {@link #establishSession} rota el id de
 * sesión (anti fixation), indexa por {@code nexus.accountId}, fija los identificadores
 * públicos de gestión de sesión, construye la autenticación con los
 * {@link FactorGrantedAuthority} (PASSWORD siempre; TOTP si procede) y persiste el
 * contexto. Reemplaza la inicialización manual que antes hacía el controlador +
 * {@code PanelSessionInitializer}.</p>
 */
@Component
public class PanelSessionAuthenticator {

    public static final String GENERIC_ERROR = "Invalid email or password.";
    private static final Duration MFA_PENDING_TTL = Duration.ofMinutes(5);
    private static final int TOTP_WINDOW_STEPS = 1;

    private final AuthenticationManager authenticationManager;
    private final NexusAccountRepository accountRepository;
    private final NexusAccountRecoveryCodeRepository recoveryCodeRepository;
    private final NexusAccountUserDetailsService userDetailsService;
    private final TotpCrypto totpCrypto;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public PanelSessionAuthenticator(
            @Qualifier("panelAuthenticationManager") AuthenticationManager authenticationManager,
            NexusAccountRepository accountRepository,
            NexusAccountRecoveryCodeRepository recoveryCodeRepository,
            NexusAccountUserDetailsService userDetailsService,
            TotpCrypto totpCrypto
    ) {
        this.authenticationManager = authenticationManager;
        this.accountRepository = accountRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.userDetailsService = userDetailsService;
        this.totpCrypto = totpCrypto;
    }

    /**
     * Verifica email + contraseña. Si la cuenta tiene MFA, fija el ticket pendiente y
     * lanza {@link MfaRequiredException} (sin sesión autenticada). Si no, establece la
     * sesión y devuelve la autenticación.
     */
    public Authentication authenticate(
            String email, String password, HttpServletRequest request, HttpServletResponse response
    ) {
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(email, password);
        Authentication authenticated;
        try {
            authenticated = authenticationManager.authenticate(token);
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException(GENERIC_ERROR);
        }
        NexusAccountPrincipal principal = (NexusAccountPrincipal) authenticated.getPrincipal();
        NexusAccount account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new BadCredentialsException(GENERIC_ERROR));
        Instant now = Instant.now();

        if (account.isMfaEnabled()) {
            request.getSession().setAttribute(NexusSessionAttributes.PANEL_MFA_PENDING,
                    new PanelMfaPendingTicket(account.getId(), now, now.plus(MFA_PENDING_TTL)));
            throw new MfaRequiredException(account.getId());
        }
        return establishSession(principal, account, now, null, request, response);
    }

    /**
     * Completa el login MFA: lee el ticket pendiente, verifica el código TOTP (o un
     * recovery code) y, si valida, establece la sesión con ambos factores. Lanza
     * {@link BadCredentialsException} si no hay ticket, está expirado o el código no
     * valida.
     */
    public Authentication completeMfaAuthentication(
            String code, HttpServletRequest request, HttpServletResponse response
    ) {
        Instant now = Instant.now();
        HttpSession session = request.getSession(false);
        Object raw = (session == null) ? null : session.getAttribute(NexusSessionAttributes.PANEL_MFA_PENDING);
        if (!(raw instanceof PanelMfaPendingTicket ticket) || ticket.expiresAt().isBefore(now)) {
            throw new BadCredentialsException(GENERIC_ERROR);
        }
        NexusAccount account = accountRepository.findById(ticket.accountId())
                .orElseThrow(() -> new BadCredentialsException(GENERIC_ERROR));
        if (!verifyTotpOrRecovery(account, code, now)) {
            throw new BadCredentialsException(GENERIC_ERROR);
        }
        // Quitar el ticket antes de rotar el id de sesión para que no migre al nuevo id.
        session.removeAttribute(NexusSessionAttributes.PANEL_MFA_PENDING);
        UserDetails userDetails = userDetailsService.loadUserByUsername(account.getEmail());
        NexusAccountPrincipal principal = (NexusAccountPrincipal) userDetails;
        return establishSession(principal, account, ticket.passwordVerifiedAt(), now, request, response);
    }

    /**
     * Verifica el segundo factor: un código TOTP de 6 dígitos (contra el secret
     * descifrado) o, en su defecto, un recovery code single-use (hash SHA-256).
     */
    private boolean verifyTotpOrRecovery(NexusAccount account, String code, Instant now) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String trimmed = code.trim();
        if (account.getTotpSecretEnc() != null && trimmed.length() == TotpGenerator.DIGITS) {
            try {
                byte[] secret = Base32.decode(totpCrypto.decrypt(account.getTotpSecretEnc()));
                if (TotpGenerator.verify(secret, trimmed, now.getEpochSecond(), TOTP_WINDOW_STEPS)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // secret corrupto: caer a recovery (no revelar)
            }
        }
        Optional<NexusAccountRecoveryCode> match = recoveryCodeRepository
                .findByCodeHashAndConsumedAtIsNull(SecureHashes.sha256Hex(trimmed));
        if (match.isPresent()) {
            match.get().consume(now);
            recoveryCodeRepository.save(match.get());
            return true;
        }
        return false;
    }

    private Authentication establishSession(
            NexusAccountPrincipal principal, NexusAccount account,
            Instant passwordVerifiedAt, Instant totpVerifiedAt,
            HttpServletRequest request, HttpServletResponse response
    ) {
        request.getSession();
        request.changeSessionId();
        HttpSession session = request.getSession();
        session.setAttribute(PanelSessionConfiguration.ACCOUNT_ID, account.getId().toString());
        session.setAttribute(NexusSessionAttributes.SESSION_PUBLIC_ID, UUID.randomUUID().toString());
        session.setAttribute(NexusSessionAttributes.USER_AGENT,
                NexusSessionAttributes.truncateUserAgent(request.getHeader("User-Agent")));

        principal.eraseCredentials();
        List<GrantedAuthority> authorities = new ArrayList<>(principal.getAuthorities());
        authorities.add(FactorGrantedAuthority
                .withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY).issuedAt(passwordVerifiedAt).build());
        if (totpVerifiedAt != null) {
            authorities.add(FactorGrantedAuthority.withFactor("TOTP").issuedAt(totpVerifiedAt).build());
        }
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);

        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        securityContextRepository.saveContext(securityContext, request, response);
        return authentication;
    }
}
