package dev.unzor.nexus.identity.persistence.repository;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
     * Registra un intento de login fallido en una sola sentencia: suma 1 al contador y, al llegar a
     * {@code maxAttempts}, fija {@code lockedUntil}. Un bloqueo ya caducado reinicia el contador a 1.
     * Las peticiones en paralelo se ordenan por el bloqueo de fila de PostgreSQL, así que ningún
     * intento se pierde (auditoría 2026-09-28, F20).
     *
     * @return filas actualizadas (0 si el usuario no existe en el proyecto)
     */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("update ProjectUser u set "
            + "u.failedLoginAttempts = case when u.lockedUntil is not null and u.lockedUntil <= :now then 1 "
            + "    else u.failedLoginAttempts + 1 end, "
            + "u.lockedUntil = case "
            + "    when (case when u.lockedUntil is not null and u.lockedUntil <= :now then 1 "
            + "          else u.failedLoginAttempts + 1 end) >= :maxAttempts then :lockUntil "
            + "    when u.lockedUntil is not null and u.lockedUntil <= :now then cast(null as Instant) "
            + "    else u.lockedUntil end "
            + "where u.projectId = :projectId and u.id = :userId")
    int recordFailedLogin(@Param("projectId") UUID projectId, @Param("userId") UUID userId,
                          @Param("now") Instant now, @Param("maxAttempts") int maxAttempts,
                          @Param("lockUntil") Instant lockUntil);

    /**
     * Sólo la versión de autorización actual del usuario (proyección fina para el
     * path de introspection, sin hidratar la entidad completa).
     */
    @Query("select u.authzVersion from ProjectUser u where u.projectId = :projectId and u.id = :userId")
    Optional<Long> findAuthzVersionByProjectIdAndId(
            @Param("projectId") UUID projectId, @Param("userId") UUID userId);

    Optional<ProjectUser> findByProjectIdAndEmailIgnoreCase(UUID projectId, String email);

    /** El login OIDC ({@code sub}) de un usuario con username es ese username. */
    Optional<ProjectUser> findByProjectIdAndUsername(UUID projectId, String username);

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
