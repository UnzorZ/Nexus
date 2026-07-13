package dev.unzor.nexus.identity.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Revoca las autorizaciones OAuth persistidas de un usuario de proyecto
 * (remediación de auditoría, hallazgo alto #4).
 *
 * <p>Suspender, desactivar o borrar un usuario revocaba las sesiones HTTP (Redis) pero
 * <b>no</b> las filas de {@code oauth2_authorization}: un refresh token persistido podía
 * seguir emitiendo access tokens nuevos (el flujo de refresh no reautentica al usuario) y,
 * en validación JWT local, el token seguía válido hasta caducar. Este servicio borra las
 * autorizaciones del usuario acotadas a los clientes del proyecto, cortando de raíz la
 * reemisión por refresh.</p>
 *
 * <p>Acota por {@code registered_client_id IN (clientes del proyecto)} — igual que
 * {@link BackChannelLogoutClientResolver} — y busca el UUID estable del usuario dentro
 * del {@link ProjectUserPrincipal} serializado en {@code attributes}. No usa
 * {@code principal_name}: ese valor conserva el sujeto OIDC (username/email), puede
 * cambiar y no es necesariamente único dentro del proyecto.</p>
 */
@Service
public class ProjectUserOAuthRevocationService {

    private static final Logger log = LoggerFactory.getLogger(ProjectUserOAuthRevocationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final BackChannelLogoutClientResolver logoutClientResolver;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectUserOAuthRevocationService(
            JdbcTemplate jdbcTemplate,
            BackChannelLogoutClientResolver logoutClientResolver,
            ApplicationEventPublisher eventPublisher
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.logoutClientResolver = logoutClientResolver;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Borra las autorizaciones OAuth del usuario (access/refresh tokens persistidos)
     * en el proyecto indicado. Idempotente: borrar filas inexistentes no lanza. Un fallo
     * de persistencia sí se propaga para impedir que la operación de seguridad confirme
     * dejando refresh tokens utilizables.
     */
    public void revokeForProjectUser(UUID projectId, UUID userId) {
        // Participa en la transacción del caller: tanto la mutación del usuario como la
        // revocación confirman o revierten juntas. Cualquier error SQL se propaga y marca
        // esa transacción para rollback.
        var logoutPlans = logoutClientResolver.resolveForProjectUser(projectId, userId);
        int deleted = jdbcTemplate.update(
                // oauth2_authorization.registered_client_id es VARCHAR(100),
                // mientras project_oauth_clients.id es UUID.
                "DELETE FROM oauth2_authorization WHERE registered_client_id IN "
                        + "(SELECT CAST(id AS TEXT) FROM project_oauth_clients WHERE project_id = ?) "
                        + "AND jsonb_path_exists(CAST(attributes AS JSONB), "
                        + "'strict $.**.userId ? (@ == $uid)', "
                        + "jsonb_build_object('uid', CAST(? AS TEXT)))",
                projectId, userId.toString());
        if (deleted > 0) {
            log.info("Revoked {} OAuth authorization(s) for project user {} in project {}.",
                    deleted, userId, projectId);
        }
        logoutPlans.forEach(plan -> eventPublisher.publishEvent(new BackChannelLogoutRequested(
                plan.principalName(), projectId, plan.issuer(), plan.targets())));
    }
}
