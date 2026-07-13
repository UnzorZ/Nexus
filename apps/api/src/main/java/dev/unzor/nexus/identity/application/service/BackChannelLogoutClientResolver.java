package dev.unzor.nexus.identity.application.service;

import com.nimbusds.jwt.JWTParser;
import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resuelve, para una sesión de usuario final que termina, qué clientes OAuth del proyecto
 * deben recibir un <b>back-channel logout token</b> (OIDC RFC 8417).
 *
 * <p>El <b>tracking sesión→cliente</b> se deriva de {@code oauth2_authorization}: un cliente
 * tiene una sesión (tokens emitidos) para el usuario si existe una autorización con ese
 * {@code principal_name} (= {@code ProjectUserPrincipal.getName()}, el sujeto OIDC) y
 * ese {@code registered_client_id}. Sin tabla adicional: reusamos las autorizaciones que SAS
 * ya persiste. La sobre-notificación es inofensiva (el RP ignora un logout token sin sesión
 * local para ese {@code sub}).
 *
 * <p>Se filtra además por el proyecto del usuario y por clientes que hayan declarado una
 * {@code backchannel_logout_uri} (los demás no reciben nada).</p>
 */
@Component
public class BackChannelLogoutClientResolver {

    private static final Logger log = LoggerFactory.getLogger(BackChannelLogoutClientResolver.class);

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

    /**
     * Captura los destinos de back-channel correspondientes al usuario estable antes de que
     * sus autorizaciones se eliminen. El issuer se lee del ID token persistido, que contiene
     * exactamente el {@code iss} usado al crear la sesión (incluido host/proxy externos).
     */
    public List<ResolvedLogout> resolveForProjectUser(UUID projectId, UUID userId) {
        List<AuthorizationTargetRow> rows = jdbc.query(
                "SELECT DISTINCT a.principal_name, a.oidc_id_token_value, "
                        + "c.id, c.client_id, c.backchannel_logout_uri "
                        + "FROM oauth2_authorization a "
                        + "JOIN project_oauth_clients c ON CAST(c.id AS TEXT) = a.registered_client_id "
                        + "WHERE c.project_id = ? AND c.backchannel_logout_uri IS NOT NULL "
                        + "AND jsonb_path_exists(CAST(a.attributes AS JSONB), "
                        + "'strict $.**.userId ? (@ == $uid)', "
                        + "jsonb_build_object('uid', CAST(? AS TEXT)))",
                (rs, rowNum) -> new AuthorizationTargetRow(
                        rs.getString("principal_name"),
                        rs.getString("oidc_id_token_value"),
                        new BackChannelLogoutTarget(
                                rs.getObject("id", UUID.class),
                                rs.getString("client_id"),
                                rs.getString("backchannel_logout_uri"))),
                projectId, userId.toString());

        return groupByLogoutIdentity(rows);
    }

    /**
     * Captura todos los destinos de back-channel de un proyecto antes de eliminar
     * masivamente sus autorizaciones. Se agrupa por sujeto e issuer porque cada
     * combinación requiere su propio logout token.
     */
    public List<ResolvedLogout> resolveForProject(UUID projectId) {
        List<AuthorizationTargetRow> rows = jdbc.query(
                "SELECT DISTINCT a.principal_name, a.oidc_id_token_value, "
                        + "c.id, c.client_id, c.backchannel_logout_uri "
                        + "FROM oauth2_authorization a "
                        + "JOIN project_oauth_clients c ON CAST(c.id AS TEXT) = a.registered_client_id "
                        + "WHERE c.project_id = ? AND c.backchannel_logout_uri IS NOT NULL",
                (rs, rowNum) -> new AuthorizationTargetRow(
                        rs.getString("principal_name"),
                        rs.getString("oidc_id_token_value"),
                        new BackChannelLogoutTarget(
                                rs.getObject("id", UUID.class),
                                rs.getString("client_id"),
                                rs.getString("backchannel_logout_uri"))),
                projectId);
        return groupByLogoutIdentity(rows);
    }

    private List<ResolvedLogout> groupByLogoutIdentity(List<AuthorizationTargetRow> rows) {
        Map<LogoutIdentity, Set<BackChannelLogoutTarget>> grouped = new LinkedHashMap<>();
        for (AuthorizationTargetRow row : rows) {
            String issuer = issuerOf(row.idToken());
            if (issuer == null) {
                log.warn("Back-channel logout target {} for principal {} has no parseable ID-token issuer; skipping.",
                        row.target().clientId(), row.principalName());
                continue;
            }
            grouped.computeIfAbsent(new LogoutIdentity(row.principalName(), issuer), ignored -> new LinkedHashSet<>())
                    .add(row.target());
        }

        List<ResolvedLogout> resolved = new ArrayList<>(grouped.size());
        grouped.forEach((identity, targets) -> resolved.add(new ResolvedLogout(
                identity.principalName(), identity.issuer(), List.copyOf(targets))));
        return List.copyOf(resolved);
    }

    private static String issuerOf(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return null;
        }
        try {
            return JWTParser.parse(idToken).getJWTClaimsSet().getIssuer();
        } catch (ParseException invalidToken) {
            return null;
        }
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

    public record ResolvedLogout(
            String principalName,
            String issuer,
            List<BackChannelLogoutTarget> targets
    ) {
    }

    private record AuthorizationTargetRow(
            String principalName,
            String idToken,
            BackChannelLogoutTarget target
    ) {
    }

    private record LogoutIdentity(String principalName, String issuer) {
    }
}
