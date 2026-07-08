package dev.unzor.nexus.admin.persistence.repository;

import dev.unzor.nexus.admin.domain.entity.NexusAccountRecoveryCode;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a persistencia para los recovery codes MFA de una {@code NexusAccount}.
 * Extiende {@code Repository} (no {@code JpaRepository}) para no heredar búsquedas
 * globales. Espejo de {@code ProjectUserRecoveryCodeRepository}.
 */
public interface NexusAccountRecoveryCodeRepository extends Repository<NexusAccountRecoveryCode, UUID> {

    <S extends NexusAccountRecoveryCode> List<S> saveAll(Iterable<S> codes);

    NexusAccountRecoveryCode save(NexusAccountRecoveryCode code);

    /** Códigos vigentes (no consumidos) de una cuenta. */
    List<NexusAccountRecoveryCode> findByNexusAccountIdAndConsumedAtIsNull(UUID nexusAccountId);

    /** Lookup por hash de código durante el login por recovery (sólo no consumidos). */
    Optional<NexusAccountRecoveryCode> findByCodeHashAndConsumedAtIsNull(String codeHash);

    void deleteByNexusAccountId(UUID nexusAccountId);
}
