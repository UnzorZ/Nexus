package dev.unzor.nexus.identity.persistence;

import dev.unzor.nexus.identity.application.service.ProjectOauthClientToRegisteredClientMapper;
import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import dev.unzor.nexus.identity.domain.enums.OauthClientStatus;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.UUID;

/**
 * {@link RegisteredClientRepository} compuesto que sirve a la vez a los clientes
 * OAuth <b>por proyecto</b> (tabla {@code project_oauth_clients}) y al cliente
 * técnico <b>global</b> (tabla SAS-default {@code oauth2_registered_client},
 * reconciliado por {@code OidcRegisteredClientBootstrap}).
 *
 * <p>Las búsquedas prueban primero el repo de proyecto; si no encuentran, delegan
 * en el {@link JdbcRegisteredClientRepository} global (donde vive el cliente
 * bootstrap). El método {@code save} se invoca sólo desde el bootstrap runner →
 * delega al global (los clientes de proyecto se persisten directamente en
 * {@code project_oauth_clients} vía su repo, no por aquí).</p>
 *
 * <p>El aislamiento entre proyectos no depende de este repositorio: cada cliente
 * tiene un id (UUID) y un client_id globalmente únicos, y las autorizaciones se
 * keyean por {@code registered_client_id} + {@code principal_name}.</p>
 */
public class CompositeRegisteredClientRepository implements RegisteredClientRepository {

    private final ProjectOauthClientRepository projectRepository;
    private final ProjectOauthClientToRegisteredClientMapper mapper;
    private final JdbcRegisteredClientRepository global;

    public CompositeRegisteredClientRepository(
            ProjectOauthClientRepository projectRepository,
            ProjectOauthClientToRegisteredClientMapper mapper,
            JdbcRegisteredClientRepository global
    ) {
        this.projectRepository = projectRepository;
        this.mapper = mapper;
        this.global = global;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        // Sólo el bootstrap runner llama a save; los clientes de proyecto se
        // persisten vía ProjectOauthClientRepository.
        global.save(registeredClient);
    }

    @Override
    public RegisteredClient findById(String id) {
        try {
            UUID uuid = UUID.fromString(id);
            return projectRepository.findById(uuid)
                    .filter(CompositeRegisteredClientRepository::isActive)
                    .map(mapper::toRegisteredClient)
                    .orElseGet(() -> global.findById(id));
        } catch (IllegalArgumentException notAUuid) {
            // El cliente bootstrap usa un id configurado (string), no un UUID.
            return global.findById(id);
        }
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return projectRepository.findByClientId(clientId)
                .filter(CompositeRegisteredClientRepository::isActive)
                .map(mapper::toRegisteredClient)
                .orElseGet(() -> global.findByClientId(clientId));
    }

    /**
     * Un cliente DISABLED no se expone como {@link RegisteredClient}: SAS lo tratará
     * como "no encontrado" y rechazará nuevos authorize, intercambio de code y uso de
     * refresh tokens (todos requieren resolver el cliente). Los access tokens en vuelo
     * caducan solos con su TTL corto.
     */
    private static boolean isActive(ProjectOauthClient client) {
        return client.getStatus() == OauthClientStatus.ACTIVE;
    }
}
