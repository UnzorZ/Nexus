package dev.unzor.nexus.identity.api.dto;

import dev.unzor.nexus.identity.domain.entity.ProjectOidcIdp;

import java.util.UUID;

/**
 * Read view of the per-project Google IdP configuration. The client secret is never
 * included; only the fact that a secret is configured ({@code secretConfigured}).
 */
public record GoogleIdpSummary(
        UUID projectId,
        String issuer,
        String clientId,
        String scope,
        boolean enabled,
        boolean autoProvision,
        boolean secretConfigured
) {
    public static GoogleIdpSummary from(ProjectOidcIdp idp) {
        return new GoogleIdpSummary(
                idp.getProjectId(),
                idp.getIssuer(),
                idp.getClientId(),
                idp.getScope(),
                idp.isEnabled(),
                idp.isAutoProvision(),
                idp.getClientSecretEnc() != null && !idp.getClientSecretEnc().isBlank());
    }
}
