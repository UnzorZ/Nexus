package dev.unzor.nexus.identity.persistence.repository;

import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a persistencia para clientes OAuth de proyecto. Las búsquedas por
 * proyecto incluyen siempre {@code projectId}; {@code findByClientId} es global
 * (los client_id son únicos worldwide, spec §9.6) y lo usa el
 * {@code CompositeRegisteredClientRepository}.
 */
public interface ProjectOauthClientRepository extends Repository<ProjectOauthClient, UUID> {

    ProjectOauthClient save(ProjectOauthClient client);

    ProjectOauthClient saveAndFlush(ProjectOauthClient client);

    void delete(ProjectOauthClient client);

    List<ProjectOauthClient> findAllByProjectId(UUID projectId);

    Optional<ProjectOauthClient> findByProjectIdAndId(UUID projectId, UUID id);

    Optional<ProjectOauthClient> findByClientId(String clientId);

    /** Global por PK: lo usa el CompositeRegisteredClientRepository para resolver
     *  un cliente desde el {@code registered_client_id} de una autorización. */
    Optional<ProjectOauthClient> findById(UUID id);

    boolean existsByClientId(String clientId);
}
