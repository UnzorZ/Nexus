package dev.unzor.nexus.metrics.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Convierte el mapa de tags de una métrica a/desde JSON (TEXT). JPA instancia
 * los {@code AttributeConverter} por su cuenta, así que el {@code ObjectMapper}
 * es estático y construido a mano (Jackson 3, {@code tools.jackson}).
 */
@Converter
public class MetricTagsConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return MAPPER.writeValueAsString(tags);
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(json, MAP_TYPE);
    }
}
