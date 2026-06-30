package dev.unzor.nexus.apikeys.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

/**
 * Convierte la lista de scopes de una API key a una sola columna TEXT unida por
 * saltos de línea (y viceversa). Evita una tabla secundaria y mantiene la
 * validación de formato en el boundary de la API.
 */
@Converter
public class ScopeListConverter implements AttributeConverter<List<String>, String> {

    private static final String SEPARATOR = "\n";

    @Override
    public String convertToDatabaseColumn(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "";
        }
        return String.join(SEPARATOR, scopes);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        return Arrays.stream(dbData.split(SEPARATOR))
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .toList();
    }
}
