package dev.unzor.nexus.identity.persistence.repository;

import dev.unzor.nexus.identity.domain.entity.ProjectOidcIdp;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Access to per-project external OIDC IdP configuration. Extends {@link Repository}
 * (not {@code JpaRepository}) so no global lookups leak across project realms.
 */
public interface ProjectOidcIdpRepository extends Repository<ProjectOidcIdp, UUID> {

    ProjectOidcIdp save(ProjectOidcIdp idp);

    Optional<ProjectOidcIdp> findByProjectId(UUID projectId);

    boolean existsByProjectId(UUID projectId);

    void delete(ProjectOidcIdp idp);
}
