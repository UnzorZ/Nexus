package dev.unzor.nexus.apikeys.persistence.repository;

import dev.unzor.nexus.apikeys.domain.entity.ProjectApiKey;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a persistencia para las API keys de los proyectos. No extiende
 * {@code JpaRepository} para no exponer {@code findById} global; las búsquedas
 * de gestión incluyen el proyecto. La resolución en runtime usa
 * {@link #findByKeyPrefix} (global por prefijo) y verifica el hash en memoria.
 */
public interface ProjectApiKeyRepository extends Repository<ProjectApiKey, UUID> {

    ProjectApiKey save(ProjectApiKey key);

    ProjectApiKey saveAndFlush(ProjectApiKey key);

    List<ProjectApiKey> findAllByProjectId(UUID projectId);

    Optional<ProjectApiKey> findByProjectIdAndId(UUID projectId, UUID id);

    List<ProjectApiKey> findByKeyPrefix(String keyPrefix);

    void delete(ProjectApiKey key);
}
