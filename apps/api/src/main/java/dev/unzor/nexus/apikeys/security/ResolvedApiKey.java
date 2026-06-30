package dev.unzor.nexus.apikeys.security;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de resolver una API key cruda a su proyecto y scopes. Es el
 * principal ({@code Authentication}) del filtro de runtime sobre {@code /api/v1/**}.
 * {@code keyPrefix} es el prefijo no sensible de la key ({@code nxs_<slug>_<partial>}),
 * para que otros módulos puedan mostrar qué key reportó sin volver a leer la
 * entidad {@code ProjectApiKey} (límite Modulith).
 */
public record ResolvedApiKey(UUID projectId, UUID keyId, String keyPrefix, List<String> scopes) {
}
