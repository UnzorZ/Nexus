package dev.unzor.nexus.projects.persistence.repository;

import dev.unzor.nexus.projects.domain.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

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

    boolean existsBySlugIgnoreCase(String slug);
}
