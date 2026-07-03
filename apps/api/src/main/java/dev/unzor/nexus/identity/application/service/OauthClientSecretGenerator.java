package dev.unzor.nexus.identity.application.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Genera {@code client_id} y {@code client_secret} para clientes OAuth de proyecto.
 * Los client_id son globalmente únicos (spec §9.6); el secreto se muestra una sola
 * vez y se persiste hasheado. Ambos son aleatorios con {@link SecureRandom}.
 */
@Component
public class OauthClientSecretGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateClientId() {
        return "nxo-" + randomToken(18);
    }

    public String generateClientSecret() {
        return "nxs-" + randomToken(32);
    }

    private String randomToken(int numBytes) {
        byte[] bytes = new byte[numBytes];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
