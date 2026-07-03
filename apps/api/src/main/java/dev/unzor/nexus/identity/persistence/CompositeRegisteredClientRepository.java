package dev.unzor.nexus.identity.persistence;

import dev.unzor.nexus.identity.application.service.ProjectOauthClientToRegisteredClientMapper;
import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
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
                .map((ProjectOauthClient c) -> mapper.toRegisteredClient(c))
                .orElseGet(() -> global.findByClientId(clientId));
    }
}
