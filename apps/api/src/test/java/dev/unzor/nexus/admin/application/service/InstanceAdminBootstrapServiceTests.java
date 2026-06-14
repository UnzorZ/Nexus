package dev.unzor.nexus.admin.application.service;

import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstanceAdminBootstrapServiceTests {

    private final NexusAccountRepository accountRepository =
            mock(NexusAccountRepository.class);
    private final InstanceAdminBootstrapService service =
            new InstanceAdminBootstrapService(accountRepository);

    @Test
    void grantsInstanceAdminToTheFirstEligibleAccount() {
        NexusAccount account = new NexusAccount("owner@example.com", "hash", "Owner");
        when(accountRepository.existsByInstanceAdminTrue()).thenReturn(false);

        boolean granted = service.grantInstanceAdminIfMissing(account);

        assertThat(granted).isTrue();
        assertThat(account.isInstanceAdmin()).isTrue();
        verify(accountRepository).acquireInstanceAdminBootstrapLock();
    }

    @Test
    void skipsGrantWhenInstanceAdminAlreadyExists() {
        NexusAccount account = new NexusAccount("member@example.com", "hash", "Member");
        when(accountRepository.existsByInstanceAdminTrue()).thenReturn(true);

        boolean granted = service.grantInstanceAdminIfMissing(account);

        assertThat(granted).isFalse();
        assertThat(account.isInstanceAdmin()).isFalse();
        verify(accountRepository).acquireInstanceAdminBootstrapLock();
    }
}
