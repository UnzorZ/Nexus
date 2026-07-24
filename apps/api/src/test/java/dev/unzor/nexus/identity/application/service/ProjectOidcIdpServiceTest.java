package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.api.dto.GoogleIdpSummary;
import dev.unzor.nexus.identity.api.requests.SaveGoogleIdpRequest;
import dev.unzor.nexus.identity.application.configuration.OidcFederationProperties;
import dev.unzor.nexus.identity.domain.entity.ProjectOidcIdp;
import dev.unzor.nexus.identity.persistence.repository.ProjectOidcIdpRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.security.OidcFederationCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectOidcIdpServiceTest {

    private final ProjectOidcIdpRepository repository = mock(ProjectOidcIdpRepository.class);
    private final ProjectLookupService projectLookupService = mock(ProjectLookupService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final OidcFederationCrypto crypto = devMasterKeyCrypto();

    private ProjectOidcIdpService service;

    @BeforeEach
    void setUp() {
        service = new ProjectOidcIdpService(repository, projectLookupService, crypto,
                new OidcFederationProperties(null, Duration.ofMinutes(5), Duration.ofMinutes(10)), eventPublisher);
    }

    @Test
    void findWithoutConfigReturnsDefaults() {
        UUID projectId = UUID.randomUUID();
        when(repository.findByProjectId(projectId)).thenReturn(Optional.empty());

        GoogleIdpSummary summary = service.find(projectId);

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.secretConfigured()).isFalse();
        assertThat(summary.issuer()).isEqualTo("https://accounts.google.com");
    }

    @Test
    void saveEncryptsTheSecretAndNeverReturnsIt() {
        UUID projectId = UUID.randomUUID();
        when(repository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(repository.save(any(ProjectOidcIdp.class))).thenAnswer(inv -> {
            ProjectOidcIdp saved = inv.getArgument(0);
            capturedEnc = saved.getClientSecretEnc();
            return saved;
        });

        GoogleIdpSummary summary = service.save(projectId,
                new SaveGoogleIdpRequest("cid", "super-secret", null, null, true, true), UUID.randomUUID());

        assertThat(summary.clientId()).isEqualTo("cid");
        assertThat(summary.enabled()).isTrue();
        assertThat(summary.autoProvision()).isTrue();
        assertThat(summary.secretConfigured()).isTrue();
        // The persisted entity must hold ciphertext, not plaintext, and it must decrypt back.
        assertThat(capturedEnc).isNotEqualTo("super-secret");
        assertThat(crypto.decrypt(capturedEnc)).isEqualTo("super-secret");
    }

    @Test
    void updateWithBlankSecretKeepsTheExistingOne() {
        UUID projectId = UUID.randomUUID();
        ProjectOidcIdp existing = new ProjectOidcIdp(projectId, "https://accounts.google.com", "cid",
                crypto.encrypt("first-secret"), "openid email profile", true, false);
        when(repository.findByProjectId(projectId)).thenReturn(Optional.of(existing));
        when(repository.save(any(ProjectOidcIdp.class))).thenAnswer(inv -> {
            ProjectOidcIdp saved = inv.getArgument(0);
            capturedEnc = saved.getClientSecretEnc();
            return saved;
        });

        GoogleIdpSummary summary = service.save(projectId,
                new SaveGoogleIdpRequest("cid", "", null, null, false, false), UUID.randomUUID());

        assertThat(summary.secretConfigured()).isTrue();
        assertThat(crypto.decrypt(capturedEnc)).isEqualTo("first-secret");
    }

    @Test
    void firstTimeConfigWithoutSecretIsRejected() {
        UUID projectId = UUID.randomUUID();
        when(repository.findByProjectId(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(projectId,
                new SaveGoogleIdpRequest("cid", "  ", null, null, true, false), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String capturedEnc;

    private static OidcFederationCrypto devMasterKeyCrypto() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"test"});
        return new OidcFederationCrypto(OidcFederationCrypto.DEV_DEFAULT_MASTER_KEY, env);
    }
}
