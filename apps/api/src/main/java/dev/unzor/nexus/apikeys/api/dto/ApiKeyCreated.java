package dev.unzor.nexus.apikeys.api.dto;

/**
 * Respuesta de crear/rotar una API key: el resumen más el secreto completo, que
 * solo se devuelve esta vez (spec §21.1: "Display full secret once").
 */
public record ApiKeyCreated(ApiKeySummary summary, String key) {
}
