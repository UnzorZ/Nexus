package dev.unzor.nexus.admin.application.events;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Propiedades de reentrega de la revocación de sesiones del panel, vinculadas al prefijo
 * {@code nexus.session.revocation}.
 *
 * <p>Controlan la reentrega de publicaciones de
 * {@code NexusAccountSessionsRevocationRequested} que quedaron incompletas (p. ej. por un
 * fallo temporal de Redis justo después del commit de la mutación de la cuenta).</p>
 *
 * @param resubmitBatchSize máximo de publicaciones reentregadas por invocación
 * @param resubmitMaxInFlight máximo de reentregas simultáneas en vuelo
 * @param resubmitMinAge edad mínima de una publicación para ser reentregada por el barrido
 *                       periódico; el arranque siempre usa cero
 */
@Validated
@ConfigurationProperties("nexus.session.revocation")
public record PanelSessionRevocationProperties(
        @DefaultValue("100") @Positive int resubmitBatchSize,
        @DefaultValue("10") @Positive int resubmitMaxInFlight,
        @DefaultValue("15s") @NotNull Duration resubmitMinAge
) {

    public PanelSessionRevocationProperties {
        Objects.requireNonNull(resubmitMinAge, "resubmitMinAge must not be null");
        if (resubmitMinAge.isNegative()) {
            throw new IllegalArgumentException("resubmitMinAge must not be negative");
        }
    }
}
