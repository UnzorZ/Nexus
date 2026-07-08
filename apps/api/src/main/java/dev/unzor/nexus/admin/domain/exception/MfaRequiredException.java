package dev.unzor.nexus.admin.domain.exception;

import java.util.UUID;

/**
 * La contraseña del panel es correcta pero la cuenta Nexus tiene MFA TOTP activa: se
 * requiere el segundo factor. Se lanza DESPUÉS de verificar la contraseña
 * (anti-enumeración). El login ha fijado un ticket efímero en la sesión; el frontend
 * debe recoger el código TOTP y completar el login vía
 * {@code POST /api/panel/v1/session/login/mfa}.
 */
public class MfaRequiredException extends RuntimeException {

    private final UUID accountId;

    public MfaRequiredException(UUID accountId) {
        super("Multi-factor authentication required.");
        this.accountId = accountId;
    }

    public UUID accountId() {
        return accountId;
    }
}
