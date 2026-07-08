package dev.unzor.nexus.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Duration;
import java.util.Objects;

/**
 * Propiedades del rate-limiting de red (por IP), vinculadas al prefijo
 * {@code nexus.ratelimit}.
 *
 * <p>Complementa el lockout por usuario ({@code nexus.identity.login.*}): aquel es
 * por cuenta y se evalúa dentro del flujo de autenticación; este límite es por IP y
 * se evalúa antes que cualquier cadena de Spring Security, rechazando la petición
 * antes de tocar la base de datos o bcrypt. Ver {@link RateLimitFilter}.
 *
 * @param enabled            activa/desactiva el limitador (fail-closed en config)
 * @param trustForwardedFor  si {@code true}, la clave por-IP usa la primera IP de
 *                           {@code X-Forwarded-For}; en caso contrario, {@code getRemoteAddr()}
 * @param evictInterval      frecuencia de la expiración de buckets idle
 * @param authCapacity       capacidad del bucket del tier AUTH (login/MFA/token)
 * @param authRefillTokens   tokens repuestos por {@code authRefillPeriod} (tier AUTH)
 * @param authRefillPeriod   periodo de reposición del tier AUTH
 * @param generalCapacity    capacidad del bucket del tier GENERAL (registro/verify/reset)
 * @param generalRefillTokens tokens repuestos por {@code generalRefillPeriod} (tier GENERAL)
 * @param generalRefillPeriod periodo de reposición del tier GENERAL
 */
@Validated
@ConfigurationProperties("nexus.ratelimit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("false") boolean trustForwardedFor,
        @DefaultValue("5m") @NotNull Duration evictInterval,
        @DefaultValue("10") @Positive int authCapacity,
        @DefaultValue("10") @Positive int authRefillTokens,
        @DefaultValue("1m") @NotNull Duration authRefillPeriod,
        @DefaultValue("20") @Positive int generalCapacity,
        @DefaultValue("20") @Positive int generalRefillTokens,
        @DefaultValue("1h") @NotNull Duration generalRefillPeriod
) {
    public RateLimitProperties {
        Objects.requireNonNull(evictInterval, "evictInterval must not be null");
        Objects.requireNonNull(authRefillPeriod, "authRefillPeriod must not be null");
        Objects.requireNonNull(generalRefillPeriod, "generalRefillPeriod must not be null");
        if (evictInterval.isNegative() || evictInterval.isZero()) {
            throw new IllegalArgumentException("evictInterval must be positive");
        }
        if (authRefillPeriod.isNegative() || authRefillPeriod.isZero()) {
            throw new IllegalArgumentException("authRefillPeriod must be positive");
        }
        if (generalRefillPeriod.isNegative() || generalRefillPeriod.isZero()) {
            throw new IllegalArgumentException("generalRefillPeriod must be positive");
        }
    }
}
