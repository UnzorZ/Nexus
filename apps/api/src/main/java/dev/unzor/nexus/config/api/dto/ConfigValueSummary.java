package dev.unzor.nexus.config.api.dto;

import dev.unzor.nexus.config.domain.entity.ProjectConfig;
import dev.unzor.nexus.config.domain.enums.ConfigValueType;

import java.time.Instant;
import java.util.UUID;

/** Vista de un valor de configuración de un proyecto. */
public record ConfigValueSummary(
        UUID id,
        String key,
        String value,
        ConfigValueType valueType,
        Instant createdAt,
        Instant updatedAt
) {
    public static ConfigValueSummary from(ProjectConfig config) {
        return new ConfigValueSummary(
                config.getId(),
                config.getKey(),
                config.getValue(),
                config.getValueType(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
