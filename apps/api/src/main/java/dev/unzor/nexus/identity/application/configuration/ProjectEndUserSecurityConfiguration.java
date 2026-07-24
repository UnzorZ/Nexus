package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.identity.application.service.ProjectAllowedOriginsResolver;
import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.identity.infrastructure.security.ProjectRealmIsolationFilter;
import dev.unzor.nexus.shared.security.SameSiteCsrfCookieFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Cadena de la API JSON de usuario final ({@code /api/p/{slug}/**}), consumida por el
 * dashboard Next.js con credenciales. Espejo de la cadena del panel (CORS +
 * CookieCsrf + SameSite + 401 JSON) pero SIN {@code DaoAuthenticationProvider}: el
 * login es manual vía {@code ProjectSessionAuthenticator} (que persiste el
 * {@code SecurityContext} en la sesión compartida — lo que el Authorization Server
 * reconoce al reanudar {@code /oauth2/authorize}). El principal autenticado se resuelve
 * desde esa misma sesión. {@code @Order(4)}: mismo orden que la cadena {@code /p/**}
 * (matchers disjuntos, sin solape).
 *
 * <p>El CORS es <b>dinámico</b> vía {@link ProjectEndUserCorsConfigurationSource}: los
 * orígenes permitidos se derivan de los clientes OAuth activos del proyecto del realm
 * (sus redirect URIs) más una base de instancia, de forma que cada app autoriza su
 * propio frontend sin que el operador edite la configuración de Nexus. La fuente se
 * inyecta explícitamente en esta cadena (no es un bean) para no afectar a
 * {@code /api/panel/**}.</p>
 */
@Configuration
class ProjectEndUserSecurityConfiguration {

    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

    @Bean
    @Order(4)
    SecurityFilterChain endUserApiSecurityFilterChain(
            HttpSecurity http,
            @Value("${nexus.session.cookie.same-site:${NEXUS_SESSION_COOKIE_SAME_SITE:Lax}}") String sameSite,
            @Value("${nexus.session.cookie.secure:${NEXUS_SESSION_COOKIE_SECURE:false}}") boolean secure,
            @Value("${nexus.frontend-base-url:http://localhost:3000}") String frontendBaseUrl,
            @Value("${nexus.allowed-dev-origins:${NEXUS_ALLOWED_DEV_ORIGINS:}}") String allowedDevOrigins,
            ProjectSlugResolver slugResolver,
            ProjectAllowedOriginsResolver allowedOriginsResolver
    ) throws Exception {
        Set<String> baselineOrigins = resolveBaselineOrigins(frontendBaseUrl, allowedDevOrigins);
        ProjectEndUserCorsConfigurationSource corsSource =
                new ProjectEndUserCorsConfigurationSource(allowedOriginsResolver, baselineOrigins);

        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieName(CSRF_COOKIE_NAME);
        csrfRepository.setHeaderName("X-XSRF-TOKEN");
        csrfRepository.setCookiePath("/");

        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        http
                .securityMatcher("/api/p/**")
                .cors(cors -> cors.configurationSource(corsSource))
                .addFilterBefore(new SameSiteCsrfCookieFilter(CSRF_COOKIE_NAME, sameSite, secure), CsrfFilter.class)
                // Aislamiento de realms: un principal de otro realm no puede operar los
                // endpoints JSON de este slug (/me, MFA, sesiones…). Tras SecurityContextHolder.
                .addFilterAfter(new ProjectRealmIsolationFilter(slugResolver), SecurityContextHolderFilter.class)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/p/*/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/p/*/login/google",
                                "/api/p/*/login/google/callback").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/p/*/login",
                                "/api/p/*/login/mfa",
                                "/api/p/*/login/google/link",
                                "/api/p/*/register",
                                "/api/p/*/verify-email",
                                "/api/p/*/verify-email/resend",
                                "/api/p/*/password-reset",
                                "/api/p/*/password-reset/confirm").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    /**
     * Orígenes de instancia que siempre se permiten en {@code /api/p/**} además de los
     * derivados por proyecto: el frontend propio de Nexus ({@code frontend-base-url},
     * p. ej. la página {@code /p/{slug}/login} hosteada por Nexus) y los orígenes de
     * desarrollo. Espejo de la resolución estática que antes vivía en
     * {@code EndUserWebConfiguration}.
     */
    private static Set<String> resolveBaselineOrigins(String frontendBaseUrl, String allowedDevOrigins) {
        Set<String> origins = new LinkedHashSet<>();
        addNormalized(origins, frontendBaseUrl);
        if (allowedDevOrigins != null && !allowedDevOrigins.isBlank()) {
            for (String raw : allowedDevOrigins.split(",")) {
                addNormalized(origins, raw);
            }
        }
        return origins;
    }

    private static void addNormalized(Set<String> origins, String rawOrigin) {
        if (rawOrigin == null) {
            return;
        }
        String normalized = normalizeBaseUrl(rawOrigin.trim());
        // El comodín "*" es incompatible con credenciales; se ignora (orígenes exactos).
        if (!normalized.isEmpty() && !"*".equals(normalized)) {
            origins.add(normalized);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
