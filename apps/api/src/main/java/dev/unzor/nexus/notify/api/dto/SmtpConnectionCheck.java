package dev.unzor.nexus.notify.api.dto;

/**
 * Resultado de una comprobación de conexión SMTP: abre el transport, negocia
 * STARTTLS (verificando el certificado) y autentica, sin enviar ningún correo.
 * {@code ok=false} lleva un {@code message} con la causa raíz legible.
 */
public record SmtpConnectionCheck(boolean ok, String message) {
}
