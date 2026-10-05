package dev.unzor.nexus.identity.api.dto;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/**
 * {@link ProjectUserDetails} + las claves de los roles del usuario en el proyecto
 * de la API key que llama. La usan sólo las lecturas de la API de proyecto
 * ({@code GET /api/v1/users/lookup} y {@code GET /api/v1/users/{userId}}): un
 * backend como FlipDaFile revalida periódicamente si un usuario sigue teniendo
 * un rol (p. ej. {@code admin}) sin esperar a que caduque su sesión.
 *
 * <p>{@code roles} sale de la misma fuente que el claim {@code roles} del ID token
 * ({@code EffectiveAuthoritiesService}), así que ambos valores coinciden siempre:
 * mismas claves, mismo orden alfabético. Vacío (nunca {@code null}) si el usuario
 * no tiene roles, y sólo los del proyecto de la llamada — nunca de otros realm.</p>
 */
public record RuntimeProjectUserDetails(
        UUID id,
        String email,
        String username,
        String displayName,
        String status,
        boolean mfaEnabled,
        Instant emailVerifiedAt,
        Instant lastLoginAt,
        Instant createdAt,
        List<String> roles
) {

    public RuntimeProjectUserDetails {
        // Orden alfabético estable y de-duplicado, igual que el claim del ID token.
        roles = roles == null ? List.of() : List.copyOf(new TreeSet<>(roles));
    }

    public static RuntimeProjectUserDetails from(ProjectUserDetails user, Collection<String> roleKeys) {
        return new RuntimeProjectUserDetails(
                user.id(),
                user.email(),
                user.username(),
                user.displayName(),
                user.status(),
                user.mfaEnabled(),
                user.emailVerifiedAt(),
                user.lastLoginAt(),
                user.createdAt(),
                roleKeys == null ? List.of() : List.copyOf(roleKeys)
        );
    }
}
