@org.springframework.modulith.NamedInterface("ProjectApiScopes")
package dev.unzor.nexus.apikeys.api;

import org.springframework.modulith.NamedInterface;

/**
 * Contrato público del API de proyecto relacionado con scopes: la anotación
 * {@link dev.unzor.nexus.apikeys.api.RequiredScope} la consumen los controladores
 * de otros módulos (p. ej. {@code registry}) para declarar el scope requerido en
 * un endpoint {@code /api/v1/**}. Sin esta publicación, importar
 * {@code RequiredScope} desde otro módulo violaría Modulith.
 */
