@org.springframework.modulith.NamedInterface("ProjectApiSecurity")
package dev.unzor.nexus.apikeys.security;

import org.springframework.modulith.NamedInterface;

/**
 * Contrato público del API de proyecto relacionado con seguridad: el principal
 * {@link dev.unzor.nexus.apikeys.security.ResolvedApiKey} lo inyecta el filtro de
 * autenticación por API key y lo consumen los controladores de otros módulos
 * (p. ej. {@code registry}) vía {@code @AuthenticationPrincipal}. Sin esta
 * publicación, importar {@code ResolvedApiKey} desde otro módulo violaría Modulith.
 */
