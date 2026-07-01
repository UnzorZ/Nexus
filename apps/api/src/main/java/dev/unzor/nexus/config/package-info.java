@org.springframework.modulith.ApplicationModule(displayName = "Config")
package dev.unzor.nexus.config;

/**
 * Configuración tipada por proyecto (pares clave/valor + flags). Gestión desde
 * el panel; lectura desde el API de proyecto ({@code /api/v1/config}) para que
 * las apps la consuman en arranque.
 */
