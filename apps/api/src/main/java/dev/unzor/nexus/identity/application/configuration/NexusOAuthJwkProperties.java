package dev.unzor.nexus.identity.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del keystore PKCS12 que aporta las claves de firma de los
 * access tokens e ID tokens emitidos por el Authorization Server.
 *
 * <p>Todos los campos son opcionales. Si no se configura ningún keystore, las
 * claves se generan en memoria en cada arranque (modo desarrollo) y se registra
 * una advertencia: los tokens no sobreviven a reinicios y la configuración no es
 * válida para producción ni para despliegues multi-instancia.</p>
 *
 * <p>En producción debe proveerse un keystore compartido por todas las
 * instancias. La {@code keystore-location} admite los prefijos habituales de
 * Spring ({@code file:}, {@code classpath:}).</p>
 */
@ConfigurationProperties(prefix = "nexus.oauth.jwk")
public record NexusOAuthJwkProperties(
        String keystoreLocation,
        String keystorePassword,
        String keyAlias,
        String keyPassword
) {
}
