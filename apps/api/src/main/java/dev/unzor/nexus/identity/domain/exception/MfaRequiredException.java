package dev.unzor.nexus.identity.domain.exception;

import java.util.UUID;

/**
 * La contraseña es correcta y la identidad está confirmada, pero el usuario tiene MFA
 * TOTP activa: se requiere el segundo factor. Se lanza DESPUÉS de verificar la
 * contraseña (anti-enumeración: sólo se alcanza con credenciales válidas). El login ha
 * fijado un ticket efímero en la sesión; el frontend debe recoger el código TOTP y
 * completar el login vía {@code POST /api/p/{slug}/login/mfa}.
 */
public class MfaRequiredException extends RuntimeException {

    private final UUID projectId;
    private final String email;

    public MfaRequiredException(UUID projectId, String email) {
        super("Multi-factor authentication required.");
        this.projectId = projectId;
        this.email = email;
    }

    public UUID projectId() {
        return projectId;
    }

    public String email() {
        return email;
    }
}
