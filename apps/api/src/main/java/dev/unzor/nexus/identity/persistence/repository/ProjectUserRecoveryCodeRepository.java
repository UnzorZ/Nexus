package dev.unzor.nexus.identity.persistence.repository;

import dev.unzor.nexus.identity.domain.entity.ProjectUserRecoveryCode;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a persistencia para los recovery codes MFA de un {@code ProjectUser}. Como
 * {@link ProjectUserRepository}, extiende {@code Repository} (no {@code JpaRepository})
 * para no heredar búsquedas globales.
 */
public interface ProjectUserRecoveryCodeRepository extends Repository<ProjectUserRecoveryCode, UUID> {

    <S extends ProjectUserRecoveryCode> List<S> saveAll(Iterable<S> codes);

    ProjectUserRecoveryCode save(ProjectUserRecoveryCode code);

    /** Códigos vigentes (no consumidos) de un usuario. */
    List<ProjectUserRecoveryCode> findByProjectUserIdAndConsumedAtIsNull(UUID projectUserId);

    /** Lookup por hash de código durante el login por recovery (sólo no consumidos). */
    Optional<ProjectUserRecoveryCode> findByCodeHashAndConsumedAtIsNull(String codeHash);

    void deleteByProjectUserId(UUID projectUserId);
}
