package io.nexus.client.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NexusJwtAuthenticationConverterTest {

    private final NexusJwtAuthenticationConverter converter = new NexusJwtAuthenticationConverter();

    @Test
    void mapsScopesToAuthoritiesAndKeepsPermissionsClaimUntouched() {
        Jwt jwt = jwt(List.of("openid", "profile"), List.of("orders.*", "orders.read"));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getName()).isEqualTo("alice");
        Collection<? extends GrantedAuthority> authorities = token.getAuthorities();
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .contains("SCOPE_openid", "SCOPE_profile");
        // permissions no se exponen como authorities (se resuelven vía @perm).
        assertThat(authorities).noneMatch(a -> a.getAuthority().startsWith("PERMISSION_"));
        assertThat(jwt.getClaimAsStringList("permissions")).containsExactly("orders.*", "orders.read");
    }

    @Test
    void principalIsSubject() {
        Jwt jwt = jwt(List.of("openid"), List.of());
        assertThat(converter.convert(jwt).getName()).isEqualTo("alice");
    }

    private static Jwt jwt(List<String> scopes, List<String> permissions) {
        var builder = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("alice")
                .claim("scope", String.join(" ", scopes));
        if (!permissions.isEmpty()) {
            builder.claim("permissions", permissions);
        }
        return builder.build();
    }
}
