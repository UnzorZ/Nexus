package dev.unzor.nexus.shared.audit;

import java.util.UUID;

/**
 * Quién ejecuta una mutación auditada. El panel actúa con una cuenta Nexus
 * ({@code NEXUS_ACCOUNT}); el API de proyecto ({@code /api/v1/**}) actúa con una
 * API key ({@code API_KEY}, id de la key, nunca el secreto).
 */
public record AuditActor(String type, String id) {

    public static AuditActor account(UUID accountId) {
        return new AuditActor("NEXUS_ACCOUNT", accountId == null ? null : accountId.toString());
    }

    public static AuditActor apiKey(UUID keyId) {
        return new AuditActor("API_KEY", keyId == null ? null : keyId.toString());
    }
}
