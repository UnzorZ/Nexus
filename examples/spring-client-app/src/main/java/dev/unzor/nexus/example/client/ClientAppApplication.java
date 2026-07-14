package dev.unzor.nexus.example.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Nexus reference application — consumes the {@code nexus-spring-boot-sdk}.
 * It's an OIDC client (browser login via Nexus) AND a resource server
 * ({@code /api/**} protected by Nexus-issued JWTs), plus it runs heartbeat,
 * declares its permissions, and caches permission snapshots — all autoconfigured
 * by the starter from the {@code nexus.*} properties. See {@code README.md}.
 */
@SpringBootApplication
public class ClientAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientAppApplication.class, args);
    }
}
