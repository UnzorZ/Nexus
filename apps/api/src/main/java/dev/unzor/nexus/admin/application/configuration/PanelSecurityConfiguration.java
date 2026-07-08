package dev.unzor.nexus.admin.application.configuration;

import dev.unzor.nexus.admin.infrastructure.security.NexusAccountAuthorityResolver;
import dev.unzor.nexus.admin.infrastructure.security.NexusAccountUserDetailsService;
import dev.unzor.nexus.shared.security.SameSiteCsrfCookieFilter;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Cadena de seguridad del panel Nexus ({@code /api/panel/**}), consumida por el dashboard
 * Next.js con credenciales. CORS + CookieCsrf + SameSite + 401 JSON. El login es JSON vía
 * {@code POST /api/panel/v1/session/login} ({@code PanelSessionController} +
 * {@link #panelAuthenticationManager}); NO hay formulario HTML (Thymeleaf eliminado): las
 * peticiones no autenticadas reciben 401, y el dashboard gestiona la redirección al login
 * Next.js en cliente.
 */
@Configuration
class PanelSecurityConfiguration {

    /**
     * Nombre de la cookie CSRF del panel. Debe coincidir con el que lee el frontend
     * Next.js ({@code XSRF-TOKEN}).
     */
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

    /**
     * Proveedor de autenticación del panel, expuesto como bean para que lo consuman la
     * cadena de filtros y el {@link #panelAuthenticationManager}.
     */
    @Bean
    DaoAuthenticationProvider panelAuthenticationProvider(
            NexusAccountUserDetailsService nexusAccountUserDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(nexusAccountUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    @Order(3)
    SecurityFilterChain panelSecurityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider authenticationProvider,
            @Value("${nexus.session.cookie.same-site:${NEXUS_SESSION_COOKIE_SAME_SITE:Lax}}") String csrfSameSite,
            @Value("${nexus.session.cookie.secure:${NEXUS_SESSION_COOKIE_SECURE:false}}") boolean csrfSecure
    ) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieName("XSRF-TOKEN");
        csrfTokenRepository.setHeaderName("X-XSRF-TOKEN");
        csrfTokenRepository.setCookiePath("/");

        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        http
                .securityMatcher("/panel/**", "/api/panel/**")
                .cors(Customizer.withDefaults())
                .addFilterBefore(
                        new SameSiteCsrfCookieFilter(CSRF_COOKIE_NAME, csrfSameSite, csrfSecure),
                        CsrfFilter.class)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/panel/v1/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/panel/v1/accounts").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/panel/v1/session/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/panel/v1/session/login/mfa").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }

    @Bean
    NexusAccountUserDetailsService nexusAccountUserDetailsService(
            NexusAccountRepository accountRepository,
            NexusAccountAuthorityResolver authorityResolver
    ) {
        return new NexusAccountUserDetailsService(accountRepository, authorityResolver);
    }

    /**
     * Expone el {@link AuthenticationManager} del panel como bean para que el endpoint
     * JSON {@code POST /api/panel/v1/session/login} pueda autenticar sin formulario HTML.
     */
    @Bean
    AuthenticationManager panelAuthenticationManager(DaoAuthenticationProvider panelAuthenticationProvider) {
        return new ProviderManager(panelAuthenticationProvider);
    }
}
