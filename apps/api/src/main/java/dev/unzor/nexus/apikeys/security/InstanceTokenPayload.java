package dev.unzor.nexus.apikeys.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contenido de un instance token (ADR-0012), serializado a JSON y guardado en
 * Redis con TTL. Es la credencial efímera que reemplaza a la API key larga en
 * latidos de alta frecuencia: un proyecto, una key y sus scopes. Nunca lleva el
 * secreto ni el hash.
 */
record InstanceTokenPayload(UUID projectId, UUID keyId, String keyPrefix, List<String> scopes, Instant expiresAt) {
}
