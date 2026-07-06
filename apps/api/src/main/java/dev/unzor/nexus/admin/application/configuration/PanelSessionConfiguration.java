package dev.unzor.nexus.admin.application.configuration;

import dev.unzor.nexus.shared.security.NexusSessionAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.IndexResolver;
import org.springframework.session.Session;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Configuración de las sesiones del panel Nexus respaldadas por Redis.
 *
 * <p>Spring Boot 4 no autoconfigura el repositorio indexado de Spring Session; debe
 * activarse explícitamente con {@link EnableRedisIndexedHttpSession}. Esta anotación
 * registra el {@code RedisIndexedSessionRepository} con el namespace {@code nexus:session};
 * el intervalo de inactividad máximo se aplica de forma configurable a través de un
 * {@link SessionRepositoryCustomizer} que lee {@code NEXUS_SESSION_TIMEOUT}.</p>
 *
 * <p>Esta clase además aporta las convenciones específicas de Nexus:</p>
 *
 * <ul>
 *   <li>los nombres de los atributos de sesión con significado de dominio,</li>
 *   <li>un {@link IndexResolver} que indexa las sesiones por el identificador de la
 *       cuenta, de modo que {@code RedisIndexedSessionRepository} pueda resolver las
 *       sesiones de una cuenta sin recorrer todo el keyspace, y</li>
 *   <li>un {@link CookieSerializer} que aplica name, path, http-only, same-site,
 *       secure y max-age desde la configuración (no solo los defaults del servlet).</li>
 * </ul>
 *
 * <p>Nunca se persisten entidades JPA en sesión; los atributos son cadenas u otros
 * valores simples y serializables con JDK.</p>
 */
@Configuration
@EnableRedisIndexedHttpSession(redisNamespace = "nexus:session")
public class PanelSessionConfiguration {

    /**
     * Índice utilizado por {@code RedisIndexedSessionRepository} para agrupar las
     * sesiones de una misma cuenta.
     */
    public static final String ACCOUNT_ID = "nexus.accountId";

    /**
     * Identificador público de la sesión, distinto del {@code JSESSIONID} interno de
     * Spring Session y del ID interno de Redis. Se usa para la gestión de sesiones
     * (listado y revocación) sin exponer identificadores internos.
     */
    public static final String SESSION_PUBLIC_ID = "nexus.sessionPublicId";

    /**
     * Cabecera {@code User-Agent} truncada, almacenada a efectos informativos en el
     * listado de sesiones.
     */
    public static final String USER_AGENT = "nexus.userAgent";

    /**
     * Longitud máxima del {@code User-Agent} almacenado en sesión.
     */
    public static final int USER_AGENT_MAX_LENGTH = 256;

    /**
     * Aplica el intervalo de inactividad máximo del repositorio desde
     * {@code NEXUS_SESSION_TIMEOUT}. Este es el valor efectivo que controla cuándo una
     * sesión respaldada por Redis expira por inactividad (no el del servlet).
     *
     * @param timeout duración de inactividad máxima; por defecto siete días.
     */
    @Bean
    SessionRepositoryCustomizer<RedisIndexedSessionRepository> sessionTimeoutCustomizer(
            @Value("${nexus.session.timeout:${NEXUS_SESSION_TIMEOUT:7d}}") Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("nexus.session.timeout must be positive");
        }
        return repository -> repository.setDefaultMaxInactiveInterval(timeout);
    }

    /**
     * Serializador de la cookie de sesión. Spring Session usa por defecto la cookie
     * {@code SESSION} (base64); Nexus expone la cookie clásica {@code JSESSIONID} y
     * aplica explícitamente todos los atributos de seguridad relevantes (name, path,
     * http-only, same-site, secure y max-age) desde la configuración
     * {@code nexus.session.cookie.*}, en lugar de depender solo de las propiedades del
     * servlet.
     */
    @Bean
    public CookieSerializer cookieSerializer(
            @Value("${nexus.session.cookie.name:${NEXUS_SESSION_COOKIE_NAME:JSESSIONID}}") String name,
            @Value("${nexus.session.cookie.path:${NEXUS_SESSION_COOKIE_PATH:/}}") String path,
            @Value("${nexus.session.cookie.http-only:${NEXUS_SESSION_COOKIE_HTTP_ONLY:true}}") boolean httpOnly,
            @Value("${nexus.session.cookie.same-site:${NEXUS_SESSION_COOKIE_SAME_SITE:Lax}}") String sameSite,
            @Value("${nexus.session.cookie.secure:${NEXUS_SESSION_COOKIE_SECURE:false}}") boolean secure,
            @Value("${nexus.session.cookie.max-age:${NEXUS_SESSION_COOKIE_MAX_AGE:7d}}") Duration maxAge) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(name);
        serializer.setCookiePath(path);
        serializer.setUseHttpOnlyCookie(httpOnly);
        serializer.setSameSite(sameSite);
        serializer.setUseSecureCookie(secure);
        serializer.setCookieMaxAge((int) Math.min(Math.max(maxAge.toSeconds(), -1L), Integer.MAX_VALUE));
        return serializer;
    }

    /**
     * Indexa las sesiones por el identificador de la cuenta.
     *
     * <p>El valor se persiste como atributo de sesión {@code nexus.accountId}; este
     * resolver lo expone bajo el nombre de índice estándar
     * {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME}, que es el único
     * nombre de índice que {@code RedisIndexedSessionRepository} consulta en
     * {@code findByIndexNameAndIndexValue}. De este modo
     * {@code findByPrincipalName(accountId)} y {@code findByIndexNameAndIndexValue(
     * PRINCIPAL_NAME_INDEX_NAME, accountId)} resuelven las sesiones de una cuenta sin
     * recorrer el keyspace. Las sesiones sin cuenta (p. ej. sesiones anónimas previas al
     * login) no generan índice.</p>
     *
     * <p>Indexar por {@code accountId} (y no por el email del principal) evita acoplar la
     * gestión de sesiones al identificador de login y permite que
     * {@code revokeAllForAccount(accountId)} funcione incluso si el email cambia.</p>
     */
    @Bean
    IndexResolver<Session> nexusAccountIdIndexResolver() {
        return session -> {
            String accountId = session.getAttribute(ACCOUNT_ID);
            if (accountId != null && !accountId.isBlank()) {
                return Map.of(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, accountId);
            }
            // Sesiones de usuario de proyecto (flujo /p/**): se indexan con un prefijo
            // distinto del accountId para que no colisionen, permitiendo revocarlas por
            // usuario (suspend/disable/delete) igual que las del panel.
            String projectUserId = session.getAttribute(NexusSessionAttributes.PROJECT_USER_ID);
            if (projectUserId != null && !projectUserId.isBlank()) {
                return Map.of(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                        NexusSessionAttributes.PROJECT_USER_INDEX_PREFIX + projectUserId);
            }
            return Map.of();
        };
    }
}
