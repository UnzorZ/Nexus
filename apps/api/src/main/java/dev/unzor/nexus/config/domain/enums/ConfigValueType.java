package dev.unzor.nexus.config.domain.enums;

/**
 * Tipo de un valor de configuración. Determina cómo se valida y se interpreta
 * el valor crudo almacenado (TEXT).
 */
public enum ConfigValueType {
    STRING,
    NUMBER,
    BOOLEAN,
    JSON
}
