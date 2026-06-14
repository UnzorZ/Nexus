package dev.unzor.nexus.admin.application.service;

import dev.unzor.nexus.admin.api.dto.NexusAccountDetails;
import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import dev.unzor.nexus.admin.domain.enums.NexusAccountStatus;
import dev.unzor.nexus.admin.domain.exception.NexusAccountNotFoundException;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetNexusAccountServiceTests {

    private final NexusAccountRepository accountRepository =
            mock(NexusAccountRepository.class);
    private final GetNexusAccountService service =
            new GetNexusAccountService(accountRepository);

    @Test
    void returnsAReadModelInsteadOfTheJpaEntity() {
        UUID accountId = UUID.randomUUID();
        NexusAccount account = mock(NexusAccount.class);
        Instant createdAt = Instant.parse("2026-06-10T10:00:00Z");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(account.getId()).thenReturn(accountId);
        when(account.getEmail()).thenReturn("owner@example.com");
        when(account.getDisplayName()).thenReturn("Project Owner");
        when(account.getStatus()).thenReturn(NexusAccountStatus.ACTIVE);
        when(account.isMfaEnabled()).thenReturn(true);
        when(account.isInstanceAdmin()).thenReturn(false);
        when(account.getCreatedAt()).thenReturn(createdAt);
        when(account.getUpdatedAt()).thenReturn(createdAt);

        NexusAccountDetails expectedDetails = new NexusAccountDetails(
                accountId,
                "owner@example.com",
                "Project Owner",
                NexusAccountStatus.ACTIVE,
                true,
                false,
                null,
                null,
                createdAt,
                createdAt
        );
        NexusAccountDetails result = service.getById(accountId);

        assertThat(result).isEqualTo(expectedDetails);
        verify(accountRepository).findById(accountId);
    }

    @Test
    void returnsAReadModelFromEmailInsteadOfTheJpaEntity() {
        String email = "owner@example.org";
        UUID randomId = UUID.randomUUID();
        NexusAccount account = mock(NexusAccount.class);
        Instant createdAt = Instant.parse("2026-06-10T10:00:00Z");

        when(accountRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(account));
        when(account.getId()).thenReturn(randomId);
        when(account.getEmail()).thenReturn(email);
        when(account.getDisplayName()).thenReturn("Project Owner");
        when(account.getStatus()).thenReturn(NexusAccountStatus.ACTIVE);
        when(account.isMfaEnabled()).thenReturn(true);
        when(account.isInstanceAdmin()).thenReturn(true);
        when(account.getCreatedAt()).thenReturn(createdAt);
        when(account.getUpdatedAt()).thenReturn(createdAt);

        NexusAccountDetails expectedDetails = new NexusAccountDetails(
                randomId,
                email,
                "Project Owner",
                NexusAccountStatus.ACTIVE,
                true,
                true,
                null,
                null,
                createdAt,
                createdAt
        );
        NexusAccountDetails result = service.getByEmail(email);

        assertThat(result).isEqualTo(expectedDetails);
        verify(accountRepository).findByEmailIgnoreCase(email);
    }



    @Test
    void rejectsAnUnknownAccount() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(accountId))
                .isInstanceOf(NexusAccountNotFoundException.class)
                .hasMessageContaining(accountId.toString());
    }
}
