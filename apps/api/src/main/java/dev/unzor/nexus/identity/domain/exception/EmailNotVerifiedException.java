package dev.unzor.nexus.identity.domain.exception;

import java.util.UUID;

/**
 * La contraseña es correcta pero el email del usuario aún no está verificado. Se lanza
 * DESPUÉS de confirmar la contraseña, de modo que no revela si el email existe
 * (anti-enumeración): sólo se alcanza con credenciales válidas. Lleva el email para
 * ofrecer el reenvío del enlace de verificación.
 */
public class EmailNotVerifiedException extends RuntimeException {

    private final UUID projectId;
    private final String email;

    public EmailNotVerifiedException(UUID projectId, String email) {
        super("Email not verified.");
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
