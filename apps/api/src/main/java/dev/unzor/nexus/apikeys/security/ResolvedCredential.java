package dev.unzor.nexus.apikeys.security;

/**
 * Cómo se autenticó una request del API de proyecto en el filtro
 * {@link ApiKeyAuthenticationFilter}. Se fija como {@code details} del
 * {@code Authentication} para que un endpoint pueda exigir la API key cruda y
 * rechazar el instance token efímero (p.ej. {@code /register} solo debe
 * bootstraparse con la key larga — endurecimiento de ADR-0012).
 */
public enum ResolvedCredential {
    /** La API key larga ({@code X-Nexus-Api-Key}). */
    API_KEY,
    /** Instance token efímero (ADR-0012, {@code X-Nexus-Instance-Token}). */
    INSTANCE_TOKEN
}
