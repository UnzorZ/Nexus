package dev.unzor.nexus.apikeys.security;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de resolver una API key cruda a su proyecto y scopes. Es el
 * principal ({@code Authentication}) del filtro de runtime sobre {@code /api/v1/**}.
 */
public record ResolvedApiKey(UUID projectId, UUID keyId, List<String> scopes) {
}
