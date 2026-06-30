package dev.unzor.nexus.apikeys.domain.enums;

/**
 * Estado operativo de una API key. La expiración se deriva de
 * {@code expires_at} (no es un estado almacenado): una key ACTIVE cuya
 * {@code expires_at} ya pasó se rechaza en tiempo de ejecución como expirada.
 */
public enum ApiKeyStatus {
    /** Activa y válida (salvo expiración). */
    ACTIVE,

    /** Deshabilitada manualmente; se rechaza en runtime. */
    DISABLED
}
