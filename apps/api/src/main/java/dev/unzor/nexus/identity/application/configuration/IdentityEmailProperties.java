package dev.unzor.nexus.identity.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.Objects;

/**
 * Validez de los tokens de email de usuarios finales (verificación de email y reseteo
 * de contraseña), prefijo {@code nexus.identity.email}.
 *
 * @param verificationExpiry  tiempo de vida del token de verificación de email
 * @param passwordResetExpiry tiempo de vida del token de reseteo de contraseña
 */
@Validated
@ConfigurationProperties("nexus.identity.email")
public record IdentityEmailProperties(
        @DefaultValue("24h") @NotNull Duration verificationExpiry,
        @DefaultValue("1h") @NotNull Duration passwordResetExpiry
) {
    public IdentityEmailProperties {
        Objects.requireNonNull(verificationExpiry, "verificationExpiry must not be null");
        Objects.requireNonNull(passwordResetExpiry, "passwordResetExpiry must not be null");
        if (verificationExpiry.isNegative() || verificationExpiry.isZero()) {
            throw new IllegalArgumentException("verificationExpiry must be positive");
        }
        if (passwordResetExpiry.isNegative() || passwordResetExpiry.isZero()) {
            throw new IllegalArgumentException("passwordResetExpiry must be positive");
        }
    }
}
