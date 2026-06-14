package dev.unzor.nexus.admin.application.service;

import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import org.springframework.stereotype.Service;

@Service
public class InstanceAdminBootstrapService {

    private final NexusAccountRepository accountRepository;

    public InstanceAdminBootstrapService(NexusAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public boolean grantInstanceAdminIfMissing(NexusAccount account) {
        accountRepository.acquireInstanceAdminBootstrapLock();

        if (accountRepository.existsByInstanceAdminTrue()) {
            return false;
        }

        account.grantInstanceAdmin();
        return true;
    }
}
