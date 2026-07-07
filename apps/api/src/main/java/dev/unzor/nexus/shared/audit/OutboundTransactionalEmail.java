package dev.unzor.nexus.shared.audit;

import java.util.UUID;

/**
 * Solicitud de envío de un email transaccional (verificación de email, reseteo de
 * contraseña, MFA) a un destinatario.
 *
 * <p>Lo publica el módulo emisor (p.ej. {@code identity}) con el {@code subject} y el
 * {@code htmlBody} ya construidos; lo consume el módulo {@code notify}, que lo entrega
 * vía SMTP y registra la fila {@code notifications} + auditoría. Vive en
 * {@code shared.audit} (ya publicado como {@code @NamedInterface("AuditEvents")} y
 * consumido por varios módulos) para evitar una dependencia cíclica en compilación y
 * no declarar un nuevo {@code @NamedInterface} (lo que provocaría el CGLIB-proxy trap
 * de Modulith sobre filtros {@code GenericFilterBean}). Mismo patrón que
 * {@link InstanceWentOffline} y {@link ProjectUserAuthoritiesChanged}.</p>
 */
public record OutboundTransactionalEmail(
        UUID projectId,
        String recipientEmail,
        String subject,
        String htmlBody
) {
}
