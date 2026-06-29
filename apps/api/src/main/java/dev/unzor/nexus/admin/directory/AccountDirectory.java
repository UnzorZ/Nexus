package dev.unzor.nexus.admin.directory;

import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio publicado del módulo {@code admin} para localizar cuentas Nexus por
 * email o por identificador.
 *
 * <p>Permite a otros módulos (p. ej. {@code projects}) resolver destinatarios de
 * invitación y enriquecer vistas de membresía sin acceder a los detalles internos
 * de persistencia de cuentas.</p>
 */
@Service
public class AccountDirectory {

    private final NexusAccountRepository accountRepository;

    public AccountDirectory(NexusAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Localiza una cuenta por su email (búsqueda insensible a mayúsculas).
     */
    public Optional<AccountSummary> findByEmail(String email) {
        return accountRepository.findByEmailIgnoreCase(email)
                .map(AccountSummary::from);
    }

    /**
     * Localiza una cuenta por su identificador.
     */
    public Optional<AccountSummary> findById(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(AccountSummary::from);
    }

    /**
     * Devuelve un mapa de resúmenes de cuenta indexado por id, para enriquecer
     * listados (p. ej. membresías) en una sola consulta y evitar N+1.
     */
    public Map<UUID, AccountSummary> findAllById(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(
                        NexusAccount::getId,
                        AccountSummary::from,
                        (left, right) -> right
                ));
    }
}
