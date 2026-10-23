package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAllowedOriginsResolverTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final String SLUG = "flip";

    @Mock
    private ProjectSlugResolver slugResolver;
    @Mock
    private ProjectOauthClientRepository clientRepository;
    @InjectMocks
    private ProjectAllowedOriginsResolver resolver;

    @Test
    void derivesOriginsFromActiveClientsRedirectAndPostLogoutUris() {
        when(slugResolver.resolveExisting(SLUG))
                .thenReturn(new ProjectAuthenticationContext(PROJECT_ID, SLUG));
        when(clientRepository.findAllByProjectId(PROJECT_ID)).thenReturn(List.of(
                activeClient(List.of("https://flip.unzor.dev/login/oauth2/code/nexus"),
                        List.of("https://flip.unzor.dev/")),
                activeClient(List.of("https://other.example/cb"), List.of())
        ));

        assertThat(resolver.originsForSlug(SLUG)).containsExactlyInAnyOrder(
                "https://flip.unzor.dev", "https://other.example");
    }

    @Test
    void excludesDisabledClients() {
        when(slugResolver.resolveExisting(SLUG))
                .thenReturn(new ProjectAuthenticationContext(PROJECT_ID, SLUG));
        ProjectOauthClient disabled = activeClient(List.of("https://disabled.example/cb"), List.of());
        disabled.disable();
        when(clientRepository.findAllByProjectId(PROJECT_ID)).thenReturn(List.of(disabled));

        assertThat(resolver.originsForSlug(SLUG)).isEmpty();
    }

    @Test
    void skipsInvalidRedirectUris() {
        when(slugResolver.resolveExisting(SLUG))
                .thenReturn(new ProjectAuthenticationContext(PROJECT_ID, SLUG));
        when(clientRepository.findAllByProjectId(PROJECT_ID)).thenReturn(List.of(
                activeClient(List.of("https://good.example/cb", "not a url", "/relative"), List.of())
        ));

        assertThat(resolver.originsForSlug(SLUG)).containsExactly("https://good.example");
    }

    @Test
    void emptyForUnresolvableSlug() {
        when(slugResolver.resolveExisting("ghost")).thenThrow(new RuntimeException("not found"));

        assertThat(resolver.originsForSlug("ghost")).isEmpty();
    }

    @Test
    void emptyForNullOrBlankSlug() {
        assertThat(resolver.originsForSlug(null)).isEmpty();
        assertThat(resolver.originsForSlug("  ")).isEmpty();
    }

    @Test
    void primaryProjectOriginIsAlphabeticallyFirst() {
        when(slugResolver.resolveExisting(SLUG))
                .thenReturn(new ProjectAuthenticationContext(PROJECT_ID, SLUG));
        when(clientRepository.findAllByProjectId(PROJECT_ID)).thenReturn(List.of(
                activeClient(List.of("https://other.example/cb"), List.of()),
                activeClient(List.of("https://flip.unzor.dev/login/oauth2/code/nexus"), List.of())
        ));

        assertThat(resolver.primaryProjectOrigin(SLUG)).contains("https://flip.unzor.dev");
    }

    /*
     * Ways the primary origin can be wrong (the links of verification, password reset and the
     * Google flow land on it):
     *  P1. Two registered origins, one an admin host behind an access wall: the alphabetical rule
     *      picks "https://flip-admin…" over "https://flip.…" ('-' < '.'), so every user lands on
     *      the wall.
     *  P2. The project's public base URL is a placeholder (the default "<slug>.nexus.local") or
     *      any origin no client registered: it must never become a link target.
     *  P3. The public base URL cannot be read (project gone, null): the old rule still answers.
     */

    @Test
    void primaryProjectOriginPrefersThePublicBaseUrlWhenItIsARegisteredOrigin() {
        when(slugResolver.resolveExisting(SLUG))
                .thenReturn(new ProjectAuthenticationContext(PROJECT_ID, SLUG));
        when(slugResolver.publicBaseUrl(SLUG)).thenReturn("https://flip.unzor.dev/");
        when(clientRepository.findAllByProjectId(PROJECT_ID)).thenReturn(List.of(
                activeClient(List.of("https://flip.unzor.dev/login/oauth2/code/nexus",
                        "https://flip-admin.unzor.dev/login/oauth2/code/nexus"), List.of())
        ));

        assertThat(resolver.primaryProjectOrigin(SLUG)).contains("https://flip.unzor.dev");
    }

    @Test
    void primaryProjectOriginIgnoresAPublicBaseUrlNoClientRegistered() {
        when(slugResolver.resolveExisting(SLUG))
                .thenReturn(new ProjectAuthenticationContext(PROJECT_ID, SLUG));
        when(slugResolver.publicBaseUrl(SLUG)).thenReturn("https://flip.nexus.local");
        when(clientRepository.findAllByProjectId(PROJECT_ID)).thenReturn(List.of(
                activeClient(List.of("https://other.example/cb"), List.of()),
                activeClient(List.of("https://flip.unzor.dev/login/oauth2/code/nexus"), List.of())
        ));

        assertThat(resolver.primaryProjectOrigin(SLUG)).contains("https://flip.unzor.dev");
    }

    @Test
    void primaryProjectOriginFallsBackWhenThePublicBaseUrlCannotBeRead() {
        when(slugResolver.resolveExisting(SLUG))
                .thenReturn(new ProjectAuthenticationContext(PROJECT_ID, SLUG));
        when(slugResolver.publicBaseUrl(SLUG)).thenThrow(new RuntimeException("gone"));
        when(clientRepository.findAllByProjectId(PROJECT_ID)).thenReturn(List.of(
                activeClient(List.of("https://flip.unzor.dev/cb", "https://flip-admin.unzor.dev/cb"), List.of())
        ));

        assertThat(resolver.primaryProjectOrigin(SLUG)).contains("https://flip-admin.unzor.dev");
    }

    @Test
    void primaryProjectOriginEmptyWhenNoRegisteredOrigins() {
        when(slugResolver.resolveExisting("ghost")).thenThrow(new RuntimeException("not found"));

        assertThat(resolver.primaryProjectOrigin("ghost")).isEmpty();
    }

    private static ProjectOauthClient activeClient(List<String> redirectUris, List<String> postLogout) {
        return new ProjectOauthClient(
                PROJECT_ID, "nxo-" + UUID.randomUUID(), null, "client",
                redirectUris, postLogout,
                List.of("authorization_code"), List.of("openid"), true, false, null);
    }
}
