package dev.unzor.nexus.identity.persistence.repository;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a persistencia para usuarios aislados por proyecto.
 *
 * <p>Todas las consultas incluyen {@code projectId}. El repositorio no extiende
 * {@code JpaRepository} para no heredar búsquedas globales que puedan atravesar
 * accidentalmente los límites entre realms OAuth/OIDC.</p>
 */
public interface ProjectUserRepository extends Repository<ProjectUser, UUID> {

    ProjectUser save(ProjectUser user);

    Optional<ProjectUser> findByProjectIdAndId(UUID projectId, UUID userId);

    Optional<ProjectUser> findByProjectIdAndEmailIgnoreCase(UUID projectId, String email);

    boolean existsByProjectIdAndEmailIgnoreCase(UUID projectId, String email);
}
