package dev.unzor.nexus.admin.application.configuration;

import dev.unzor.nexus.admin.infrastructure.security.PanelAuthenticationFailureHandler;
import dev.unzor.nexus.admin.infrastructure.security.PanelAuthenticationSuccessHandler;
import dev.unzor.nexus.admin.infrastructure.security.PanelContinueUrlValidator;
import dev.unzor.nexus.admin.infrastructure.security.NexusAccountAuthorityResolver;
import dev.unzor.nexus.admin.infrastructure.security.NexusAccountUserDetailsService;
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
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
class PanelSecurityConfiguration {

    /**
     * Proveedor de autenticación del panel, expuesto como bean para que lo
     * consuman tanto la cadena de filtros como el {@link #panelAuthenticationManager}.
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
            PanelAuthenticationSuccessHandler authenticationSuccessHandler,
            PanelAuthenticationFailureHandler authenticationFailureHandler
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
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/panel/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/panel/v1/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/panel/v1/accounts").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/panel/v1/session/login").permitAll()
                        .requestMatchers("/panel/logout").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider)
                .formLogin(formLogin -> formLogin
                        .loginPage("/panel/login")
                        .loginProcessingUrl("/panel/login")
                        .successHandler(authenticationSuccessHandler)
                        .failureHandler(authenticationFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/panel/logout")
                        .logoutSuccessUrl("/panel/login?logout")
                        .permitAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getServletPath().startsWith("/api/panel/")
                        )
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/panel/login"),
                                request -> request.getServletPath().startsWith("/panel/")
                        )
                );

        return http.build();
    }

    @Bean
    NexusAccountUserDetailsService nexusAccountUserDetailsService(
            NexusAccountRepository accountRepository,
            NexusAccountAuthorityResolver authorityResolver
    ) {
        return new NexusAccountUserDetailsService(accountRepository, authorityResolver);
    }

    @Bean
    PanelContinueUrlValidator panelContinueUrlValidator(
            @Value("${nexus.frontend-base-url:http://localhost:3000}") String frontendBaseUrl
    ) {
        return new PanelContinueUrlValidator(frontendBaseUrl);
    }

    @Bean
    PanelAuthenticationSuccessHandler panelAuthenticationSuccessHandler(
            PanelContinueUrlValidator continueUrlValidator
    ) {
        return new PanelAuthenticationSuccessHandler(continueUrlValidator);
    }

    /**
     * Expone el {@link AuthenticationManager} del panel como bean para que el
     * endpoint JSON {@code POST /api/panel/v1/session/login} pueda autenticar sin
     * depender de la redirección del formulario HTML.
     */
    @Bean
    AuthenticationManager panelAuthenticationManager(DaoAuthenticationProvider panelAuthenticationProvider) {
        return new ProviderManager(panelAuthenticationProvider);
    }

    @Bean
    PanelAuthenticationFailureHandler panelAuthenticationFailureHandler(
            PanelContinueUrlValidator continueUrlValidator
    ) {
        return new PanelAuthenticationFailureHandler(continueUrlValidator);
    }
}
