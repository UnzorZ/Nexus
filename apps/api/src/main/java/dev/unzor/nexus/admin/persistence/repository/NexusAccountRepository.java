package dev.unzor.nexus.admin.persistence.repository;

import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a persistencia para las cuentas globales de una instancia Nexus.
 *
 * <p>Las consultas no necesitan {@code projectId}: una {@link NexusAccount} existe
 * fuera del alcance de cualquier proyecto. La autorización sobre proyectos se
 * resuelve posteriormente mediante membresías.</p>
 */
public interface NexusAccountRepository extends JpaRepository<NexusAccount, UUID> {

    Optional<NexusAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByInstanceAdminTrue();

    /**
     * Serializes the first-account bootstrap within the current transaction.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(721940317)", nativeQuery = true)
    void acquireInstanceAdminBootstrapLock();
}
