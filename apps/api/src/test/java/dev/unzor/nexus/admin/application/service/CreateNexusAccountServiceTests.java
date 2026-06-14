package dev.unzor.nexus.admin.application.service;

import dev.unzor.nexus.admin.api.dto.NexusAccountDetails;
import dev.unzor.nexus.admin.api.requests.CreateNexusAccountRequest;
import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import dev.unzor.nexus.admin.domain.enums.NexusAccountStatus;
import dev.unzor.nexus.admin.domain.exception.NexusAccountEmailAlreadyExistsException;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateNexusAccountServiceTests {

    private final NexusAccountRepository accountRepository =
            mock(NexusAccountRepository.class);
    private final PasswordEncoder passwordEncoder =
            mock(PasswordEncoder.class);
    private final InstanceAdminBootstrapService instanceAdminBootstrapService =
            mock(InstanceAdminBootstrapService.class);
    private final CreateNexusAccountService service =
            new CreateNexusAccountService(
                    accountRepository,
                    passwordEncoder,
                    instanceAdminBootstrapService
            );

    @Test
    void normalizesEmailAndHashesPasswordBeforePersisting() {
        when(accountRepository.existsByEmailIgnoreCase("owner@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(accountRepository.saveAndFlush(any(NexusAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(instanceAdminBootstrapService.grantInstanceAdminIfMissing(any()))
                .thenAnswer(invocation -> {
                    NexusAccount account = invocation.getArgument(0);
                    account.grantInstanceAdmin();
                    return true;
                });

        NexusAccountDetails result = service.create(
                new CreateNexusAccountRequest(
                        "  Owner@Example.COM  ",
                        "plain-password",
                        "  Project Owner  "
                )
        );

        ArgumentCaptor<NexusAccount> accountCaptor = ArgumentCaptor.forClass(NexusAccount.class);
        verify(accountRepository).saveAndFlush(accountCaptor.capture());

        NexusAccount savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getEmail()).isEqualTo("owner@example.com");
        assertThat(savedAccount.getDisplayName()).isEqualTo("Project Owner");
        assertThat(savedAccount.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedAccount.getStatus()).isEqualTo(NexusAccountStatus.ACTIVE);

        assertThat(result.email()).isEqualTo("owner@example.com");
        assertThat(result.instanceAdmin()).isTrue();
        verify(passwordEncoder).encode("plain-password");
        verify(instanceAdminBootstrapService).grantInstanceAdminIfMissing(savedAccount);
    }

    @Test
    void rejectsDuplicateEmails() {
        when(accountRepository.existsByEmailIgnoreCase("owner@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateNexusAccountRequest(
                        "owner@example.com",
                        "plain-password",
                        "Project Owner"
                )
        ))
                .isInstanceOf(NexusAccountEmailAlreadyExistsException.class)
                .hasMessageContaining("owner@example.com");

        verify(accountRepository, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(any());
        verify(instanceAdminBootstrapService, never()).grantInstanceAdminIfMissing(any());
    }

    @Test
    void translatesConcurrentDuplicateEmailToDomainConflict() {
        when(accountRepository.existsByEmailIgnoreCase("owner@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(accountRepository.saveAndFlush(any(NexusAccount.class)))
                .thenThrow(uniqueEmailViolation());

        assertThatThrownBy(() -> service.create(
                new CreateNexusAccountRequest(
                        "owner@example.com",
                        "plain-password",
                        "Project Owner"
                )
        ))
                .isInstanceOf(NexusAccountEmailAlreadyExistsException.class)
                .hasMessageContaining("owner@example.com");

        verify(instanceAdminBootstrapService, never()).grantInstanceAdminIfMissing(any());
    }

    private static DataIntegrityViolationException uniqueEmailViolation() {
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate email",
                new SQLException("duplicate email"),
                "uk_nexus_accounts_email"
        );
        return new DataIntegrityViolationException("duplicate email", cause);
    }
}
