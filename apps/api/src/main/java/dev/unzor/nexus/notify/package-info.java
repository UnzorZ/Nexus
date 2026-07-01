@org.springframework.modulith.ApplicationModule(displayName = "Notify")
package dev.unzor.nexus.notify;

/**
 * Notificaciones por proyecto: plantillas (email) y envío desde el API de
 * proyecto ({@code /api/v1/notify}). El canal email usa SMTP configurado por
 * {@code nexus.notify.smtp.*}.
 */
