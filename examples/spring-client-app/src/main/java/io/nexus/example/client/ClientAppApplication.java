package io.nexus.example.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Nexus reference application: an OIDC client (browser login via Nexus) that is
 * also a resource server (its {@code /api/**} endpoints are protected by
 * Nexus-issued JWTs).
 *
 * <p>See {@code README.md} for the end-to-end walkthrough and
 * {@code SecurityConfig} / {@code ResourceServerSecurity} for the two security
 * filter chains (client + resource server).</p>
 */
@SpringBootApplication
public class ClientAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientAppApplication.class, args);
    }
}
