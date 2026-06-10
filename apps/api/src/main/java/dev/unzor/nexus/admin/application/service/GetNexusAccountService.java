package dev.unzor.nexus.admin.application.service;

import dev.unzor.nexus.admin.api.dto.NexusAccountDetails;
import dev.unzor.nexus.admin.domain.exception.NexusAccountNotFoundException;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso de lectura para obtener información pública de una cuenta Nexus.
 *
 * <p>El repositorio carga la entidad dentro de una transacción de solo lectura. El
 * servicio la transforma inmediatamente en {@link NexusAccountDetails}, evitando
 * que controladores u otros consumidores reciban una entidad gestionada por JPA o
 * puedan acceder al hash de contraseña.</p>
 */
@Service
public class GetNexusAccountService {

    private final NexusAccountRepository accountRepository;

    public GetNexusAccountService(NexusAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Obtiene una vista inmutable de la cuenta.
     *
     * @throws NexusAccountNotFoundException cuando no existe la cuenta solicitada
     */
    @Transactional(readOnly = true)
    public NexusAccountDetails getById(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(NexusAccountDetails::from)
                .orElseThrow(() -> new NexusAccountNotFoundException(accountId));
    }

    @Transactional(readOnly = true)
    public NexusAccountDetails getByEmail(String email) {
        return accountRepository.findByEmailIgnoreCase(email)
                .map(NexusAccountDetails::from)
                .orElseThrow(() -> new NexusAccountNotFoundException(null));
        }
}
