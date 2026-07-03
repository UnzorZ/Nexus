package dev.unzor.nexus.identity.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Propiedades de endurecimiento del login de usuarios de proyecto, vinculadas al
 * prefijo {@code nexus.identity.login}.
 *
 * @param maxAttempts     intentos fallidos consecutivos antes de bloquear al usuario
 * @param lockoutDuration duración del bloqueo temporal tras alcanzar {@code maxAttempts}
 */
@Validated
@ConfigurationProperties("nexus.identity.login")
public record IdentityLoginProperties(
        @DefaultValue("5") @Positive int maxAttempts,
        @DefaultValue("15m") @NotNull Duration lockoutDuration
) {

    public IdentityLoginProperties {
        Objects.requireNonNull(lockoutDuration, "lockoutDuration must not be null");
        if (lockoutDuration.isNegative() || lockoutDuration.isZero()) {
            throw new IllegalArgumentException("lockoutDuration must be positive");
        }
    }
}
