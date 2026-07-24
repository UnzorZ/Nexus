package dev.unzor.nexus.identity.persistence.repository;

import dev.unzor.nexus.identity.domain.entity.ProjectUserOidcLink;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Access to the federation links between project users and external IdP subjects.
 * Every query is scoped by {@code projectId} so a subject of one realm can never be
 * resolved against the users of another.
 */
public interface ProjectUserOidcLinkRepository extends Repository<ProjectUserOidcLink, UUID> {

    ProjectUserOidcLink save(ProjectUserOidcLink link);

    /**
     * Resolves a link by the external subject, scoped to the project. This is the entry
     * point of a federated callback: a known subject logs its owner in directly.
     */
    Optional<ProjectUserOidcLink> findByProjectIdAndProviderAndSubject(
            UUID projectId, String provider, String subject);

    Optional<ProjectUserOidcLink> findByProjectUserIdAndProvider(UUID projectUserId, String provider);

    void delete(ProjectUserOidcLink link);
}
