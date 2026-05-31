package dev.unzor.nexus.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/internal/**", "/actuator/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers("/internal/**", "/actuator/**"))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                "/internal/**",
                                "/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
