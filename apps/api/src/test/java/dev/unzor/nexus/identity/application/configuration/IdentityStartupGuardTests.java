package dev.unzor.nexus.identity.application.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitarios del guard fail-closed (B3): cubren la lógica de decisión sin
 * levantar el contexto. La propagación de la excepción desde el {@code ApplicationRunner}
 * aborta el arranque, por lo que verificar que {@code run(...)} lanza es suficiente.
 */
class IdentityStartupGuardTests {

    private static final String DEV_SECRET = "changeme-local-dev";
    private static final String DEV_KEYSTORE = "classpath:keystore/dev-jwk.p12";

    @Test
    void permitsDevSecretsUnderDefaultProfile() {
        IdentityStartupGuard guard = guard(new String[0], DEV_SECRET, DEV_KEYSTORE);
        assertThatCode(() -> guard.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void permitsDevSecretsUnderKnownDevProfile() {
        IdentityStartupGuard guard = guard(new String[]{"remote-dev"}, DEV_SECRET, DEV_KEYSTORE);
        assertThatCode(() -> guard.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void refusesDevBootstrapSecretUnderProdProfile() {
        IdentityStartupGuard guard = guard(new String[]{"prod"}, DEV_SECRET, "/etc/nexus/keystore.p12");
        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development OAuth identity secrets");
    }

    @Test
    void refusesDevJwkKeystoreUnderProdProfile() {
        IdentityStartupGuard guard = guard(new String[]{"prod"}, "a-real-secret", DEV_KEYSTORE);
        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void permitsRealSecretsUnderProdProfile() {
        IdentityStartupGuard guard = guard(new String[]{"prod"}, "a-real-production-secret", "/etc/nexus/keystore.p12");
        assertThatCode(() -> guard.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    private static IdentityStartupGuard guard(String[] activeProfiles, String secret, String keystoreLocation) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return new IdentityStartupGuard(
                environment,
                new NexusOAuthBootstrapProperties(null, null, secret, null, null),
                new NexusOAuthJwkProperties(keystoreLocation, null, null, null)
        );
    }
}
