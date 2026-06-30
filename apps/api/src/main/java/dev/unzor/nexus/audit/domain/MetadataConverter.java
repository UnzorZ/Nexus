package dev.unzor.nexus.audit.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Convierte el metadata libre de un evento de auditoría a una columna TEXT con
 * JSON (y viceversa). Mismo patrón que {@code registry.domain.MetadataConverter}:
 * ObjectMapper estático porque JPA instancia los converters (no Spring), y TEXT
 * (no jsonb) para encajar con el tipo String y evitar el binding JDBC de jsonb.
 */
@Converter
public class MetadataConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el metadata de auditoría", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo deserializar el metadata de auditoría", e);
        }
    }
}
