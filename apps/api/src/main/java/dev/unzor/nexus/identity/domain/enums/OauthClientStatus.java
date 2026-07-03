package dev.unzor.nexus.identity.domain.enums;

/**
 * Estado de un cliente OAuth de proyecto. {@code ACTIVE} puede emitir tokens;
 * {@code DISABLED} se rechaza en el flujo de autorización.
 */
public enum OauthClientStatus {
    ACTIVE,
    DISABLED
}
