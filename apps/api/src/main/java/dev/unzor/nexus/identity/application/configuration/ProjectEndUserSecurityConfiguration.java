package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.shared.security.SameSiteCsrfCookieFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Cadena de la API JSON de usuario final ({@code /api/p/{slug}/**}), consumida por el
 * dashboard Next.js con credenciales. Espejo de la cadena del panel (CORS +
 * CookieCsrf + SameSite + 401 JSON) pero SIN {@code DaoAuthenticationProvider}: el
 * login es manual vía {@code ProjectSessionAuthenticator} (que persiste el
 * {@code SecurityContext} en la sesión compartida — lo que el Authorization Server
 * reconoce al reanudar {@code /oauth2/authorize}). El principal autenticado se resuelve
 * desde esa misma sesión. {@code @Order(4)}: mismo orden que la cadena {@code /p/**}
 * (matchers disjuntos, sin solape).
 */
@Configuration
class ProjectEndUserSecurityConfiguration {

    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

    @Bean
    @Order(4)
    SecurityFilterChain endUserApiSecurityFilterChain(
            HttpSecurity http,
            @Value("${nexus.session.cookie.same-site:${NEXUS_SESSION_COOKIE_SAME_SITE:Lax}}") String sameSite,
            @Value("${nexus.session.cookie.secure:${NEXUS_SESSION_COOKIE_SECURE:false}}") boolean secure
    ) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieName(CSRF_COOKIE_NAME);
        csrfRepository.setHeaderName("X-XSRF-TOKEN");
        csrfRepository.setCookiePath("/");

        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        http
                .securityMatcher("/api/p/**")
                .cors(Customizer.withDefaults())
                .addFilterBefore(new SameSiteCsrfCookieFilter(CSRF_COOKIE_NAME, sameSite, secure), CsrfFilter.class)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/p/*/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/p/*/login",
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
}
