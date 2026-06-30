package dev.unzor.nexus.registry.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Convierte el metadata libre (clave-valor) de un heartbeat a una columna TEXT
 * con JSON (y viceversa). Usa un {@link ObjectMapper} estático porque los
 * {@link AttributeConverter} los instancta JPA, no Spring, por lo que no pueden
 * recibir el bean de ObjectMapper por inyección. Columna TEXT (no jsonb) para
 * encajar con el tipo String del converter y evitar el binding JDBC de jsonb.
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
            throw new IllegalStateException("No se pudo serializar el metadata del heartbeat", e);
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
            throw new IllegalStateException("No se pudo deserializar el metadata del heartbeat", e);
        }
    }
}
