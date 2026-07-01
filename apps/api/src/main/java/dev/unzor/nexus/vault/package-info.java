@org.springframework.modulith.ApplicationModule(displayName = "Vault")
package dev.unzor.nexus.vault;

/**
 * Secretos cifrados por proyecto (AES-256-GCM). El panel escribe/rota/borra
 * (sin leer jamás el valor); las apps leen el valor descifrado desde el API de
 * proyecto ({@code /api/v1/vault}, scope {@code vault:read}, auditado).
 */
