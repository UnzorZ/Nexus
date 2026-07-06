package dev.unzor.nexus.vault.api.dto;

/** Valor descifrado de un secreto (respuesta del runtime read). */
public record SecretValue(String key, String value) {
}
