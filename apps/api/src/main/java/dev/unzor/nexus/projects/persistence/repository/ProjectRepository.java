package dev.unzor.nexus.projects.persistence.repository;

import dev.unzor.nexus.projects.domain.entity.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a persistencia para el registro global de proyectos.
 *
 * <p>Los proyectos pueden localizarse globalmente por ID o slug. Obtener un
 * proyecto no autoriza por sí mismo a una cuenta Nexus: el acceso debe comprobarse
 * mediante una membresía activa o el privilegio de administración de instancia.</p>
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findBySlugIgnoreCase(String slug);

    /**
     * Shared row lock used to serialize OAuth authorization writes with project
     * archival. The caller must keep the surrounding transaction open while it
     * persists the authorization.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Project p where p.id = :projectId")
    Optional<Project> findForShareById(@Param("projectId") UUID projectId);

    boolean existsBySlugIgnoreCase(String slug);
}
