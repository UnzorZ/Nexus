package dev.unzor.nexus.modules.api.dto;

/**
 * Estado efectivo de un módulo para un proyecto, incluyendo su valor por defecto del catálogo.
 */
public record ProjectModuleStatus(String key, boolean enabled, boolean enabledByDefault) {
}
