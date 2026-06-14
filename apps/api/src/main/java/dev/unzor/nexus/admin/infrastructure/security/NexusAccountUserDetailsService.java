package dev.unzor.nexus.admin.infrastructure.security;

import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import dev.unzor.nexus.admin.persistence.repository.NexusAccountRepository;
import dev.unzor.nexus.shared.validation.EmailNormalizer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class NexusAccountUserDetailsService implements UserDetailsService {

    private final NexusAccountRepository accountRepository;
    private final NexusAccountAuthorityResolver authorityResolver;

    public NexusAccountUserDetailsService(
            NexusAccountRepository accountRepository,
            NexusAccountAuthorityResolver authorityResolver
    ) {
        this.accountRepository = accountRepository;
        this.authorityResolver = authorityResolver;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String normalizedEmail = EmailNormalizer.normalize(username);
        NexusAccount account = accountRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException(normalizedEmail));

        return NexusAccountPrincipal.from(
                account,
                authorityResolver.resolve(account)
        );
    }
}
