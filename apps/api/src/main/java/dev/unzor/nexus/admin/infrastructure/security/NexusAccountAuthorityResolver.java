package dev.unzor.nexus.admin.infrastructure.security;

import dev.unzor.nexus.admin.domain.entity.NexusAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class NexusAccountAuthorityResolver {

    static final String ROLE_USER = "ROLE_USER";
    static final String ROLE_INSTANCE_ADMIN = "ROLE_INSTANCE_ADMIN";

    public Collection<? extends GrantedAuthority> resolve(NexusAccount account) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(ROLE_USER));

        if (account.isInstanceAdmin()) {
            authorities.add(new SimpleGrantedAuthority(ROLE_INSTANCE_ADMIN));
        }

        return authorities;
    }
}
