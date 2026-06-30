package dev.unzor.nexus.apikeys.application.configuration;

import dev.unzor.nexus.apikeys.infrastructure.interceptor.RequiredScopeInterceptor;
import dev.unzor.nexus.apikeys.security.ApiKeyAuthenticationFilter;
import dev.unzor.nexus.apikeys.security.ApiKeyResolver;
import dev.unzor.nexus.apikeys.security.ProjectApiProblemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cadena de seguridad del API de proyecto ({@code /api/v1/**}): autenticación
 * por API key (sin sesión, CSRF off) vía {@link ApiKeyAuthenticationFilter}, más
 * el interceptor de scopes. {@code @Order(4)} coincide con la cadena de proyecto
 * de identity ({@code /p/**}) a propósito: sus {@code securityMatcher} son
 * disjuntos, así que cada request usa la cadena correcta.
 */
@Configuration
public class ApiKeySecurityConfiguration implements WebMvcConfigurer {

    private final RequiredScopeInterceptor requiredScopeInterceptor;

    public ApiKeySecurityConfiguration(RequiredScopeInterceptor requiredScopeInterceptor) {
        this.requiredScopeInterceptor = requiredScopeInterceptor;
    }

    @Bean
    @Order(4)
    public SecurityFilterChain apiKeySecurityFilterChain(
            HttpSecurity http,
            ApiKeyResolver resolver,
            ProjectApiProblemWriter problemWriter
    ) throws Exception {
        http
                .securityMatcher("/api/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new ApiKeyAuthenticationFilter(resolver, problemWriter),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
        return http.build();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requiredScopeInterceptor).addPathPatterns("/api/v1/**");
    }
}
