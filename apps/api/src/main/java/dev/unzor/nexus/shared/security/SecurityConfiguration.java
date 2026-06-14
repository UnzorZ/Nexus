package dev.unzor.nexus.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad compartida para endpoints internos y de operaciones.
 * <p>
 * Forma parte del esquema ordenado de cadenas de filtros de Nexus. Esta es la cadena
 * {@code @Order(2)}: se evalúa después del Authorization Server ({@code @Order(1)}) y
 * antes del panel Nexus ({@code @Order(3)}), las rutas de proyecto ({@code @Order(4)})
 * y la cadena residual ({@code @Order(5)}).
 * <p>
 * Los health-checks de módulos bajo {@code /internal/**} deben ser accesibles sin
 * credenciales para facilitar comprobaciones locales y de despliegue. El resto de
 * endpoints de Actuator quedan protegidos con HTTP Basic.
 *
 * @see dev.unzor.nexus.identity.application.configuration.SecurityConfig
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Cadena de filtros para rutas internas y Actuator ({@code @Order(2)}).
     * <p>
     * Coincide solo con {@code /internal/**} y {@code /actuator/**}. Desactiva CSRF en
     * esas rutas para permitir llamadas programáticas (por ejemplo {@code curl} o probes).
     * <ul>
     *   <li>{@code /internal/**} y {@code /actuator/health/**} (incluye liveness y readiness) → acceso público</li>
     *   <li>Resto de {@code /actuator/**} → requiere HTTP Basic</li>
     * </ul>
     */
    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/internal/**", "/actuator/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers("/internal/**", "/actuator/**"))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                "/internal/**",
                                "/actuator/health",
                                "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
