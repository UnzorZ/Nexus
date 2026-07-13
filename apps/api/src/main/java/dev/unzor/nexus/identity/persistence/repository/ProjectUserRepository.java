package dev.unzor.nexus.identity.persistence.repository;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    /**
     * Sólo la versión de autorización actual del usuario (proyección fina para el
     * path de introspection, sin hidratar la entidad completa).
     */
    @Query("select u.authzVersion from ProjectUser u where u.projectId = :projectId and u.id = :userId")
    Optional<Long> findAuthzVersionByProjectIdAndId(
            @Param("projectId") UUID projectId, @Param("userId") UUID userId);

    Optional<ProjectUser> findByProjectIdAndEmailIgnoreCase(UUID projectId, String email);

    boolean existsByProjectIdAndEmailIgnoreCase(UUID projectId, String email);

    List<ProjectUser> findAllByProjectId(UUID projectId);

    /**
     * Proyección fina usada para revocar las sesiones Redis de todo un proyecto sin
     * hidratar las entidades de usuario.
     */
    @Query("select u.id from ProjectUser u where u.projectId = :projectId")
    List<UUID> findIdsByProjectId(@Param("projectId") UUID projectId);

    /**
     * Búsqueda por token de verificación de email, acotada al proyecto. El token es
     * un secreto único (no permite enumeración); el projectId refuerza el aislamiento
     * entre realms. {@code null} si no hay token pendiente o ya fue consumido.
     */
    Optional<ProjectUser> findByProjectIdAndEmailVerificationTokenHash(UUID projectId, String tokenHash);

    /**
     * Búsqueda por token de reseteo de contraseña, acotada al proyecto.
     */
    Optional<ProjectUser> findByProjectIdAndPasswordResetTokenHash(UUID projectId, String tokenHash);

    void delete(ProjectUser user);
}
