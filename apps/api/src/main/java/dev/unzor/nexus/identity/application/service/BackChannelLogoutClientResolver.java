package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resuelve, para una sesión de usuario final que termina, qué clientes OAuth del proyecto
 * deben recibir un <b>back-channel logout token</b> (OIDC RFC 8417).
 *
 * <p>El <b>tracking sesión→cliente</b> se deriva de {@code oauth2_authorization}: un cliente
 * tiene una sesión (tokens emitidos) para el usuario si existe una autorización con ese
 * {@code principal_name} (= {@code ProjectUserPrincipal.getName()}, el username del realm) y
 * ese {@code registered_client_id}. Sin tabla adicional: reusamos las autorizaciones que SAS
 * ya persiste. La sobre-notificación es inofensiva (el RP ignora un logout token sin sesión
 * local para ese {@code sub}).
 *
 * <p>Se filtra además por el proyecto del usuario y por clientes que hayan declarado una
 * {@code backchannel_logout_uri} (los demás no reciben nada).</p>
 */
@Component
public class BackChannelLogoutClientResolver {

    private final JdbcTemplate jdbc;
    private final ProjectOauthClientRepository clientRepository;

    public BackChannelLogoutClientResolver(JdbcTemplate jdbc, ProjectOauthClientRepository clientRepository) {
        this.jdbc = jdbc;
        this.clientRepository = clientRepository;
    }

    /**
     * Clientes del {@code projectId} con una autorización para {@code principalName} y una
     * backchannel_logout_uri declarada.
     */
    public List<ProjectOauthClient> resolve(String principalName, UUID projectId) {
        List<String> registeredClientIds = jdbc.queryForList(
                "SELECT DISTINCT registered_client_id FROM oauth2_authorization WHERE principal_name = ?",
                String.class, principalName);
        return registeredClientIds.stream()
                .map(BackChannelLogoutClientResolver::tryParseUuid)
                .flatMap(Optional::stream)
                .map(clientRepository::findById)
                .flatMap(Optional::stream)
                .filter(client -> projectId.equals(client.getProjectId()))
                .filter(client -> client.getBackchannelLogoutUri() != null)
                .toList();
    }

    private static Optional<UUID> tryParseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException notAUuid) {
            // El cliente bootstrap usa un id configurado (string), no un UUID.
            return Optional.empty();
        }
    }
}
