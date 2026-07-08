package dev.unzor.nexus.registry.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

/**
 * Convierte una lista de destinatarios de alerta offline a una sola columna TEXT
 * unida por saltos de línea (tabla {@code project_registry_settings}). Mantiene el
 * aislamiento de módulo (no reusa el converter de {@code identity} ni de
 * {@code apikeys}) y la validación en el boundary del servicio.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final String SEPARATOR = "\n";

    @Override
    public String convertToDatabaseColumn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(SEPARATOR, values);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        return Arrays.stream(dbData.split(SEPARATOR))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
