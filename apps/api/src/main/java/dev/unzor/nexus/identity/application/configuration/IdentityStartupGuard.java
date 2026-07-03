package dev.unzor.nexus.identity.application.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Guardia de arranque <i>fail-closed</i> (B3): impide que la aplicación arranque
 * si se usan los secretos de desarrollo por defecto (el secret del cliente OAuth
 * bootstrap {@code changeme-local-dev} y/o el keystore JWK dev commiteado en el
 * repo) bajo un perfil que no sea de desarrollo.
 *
 * <p>Los perfiles en los que se toleran los secretos dev son: ninguno (perfil por
 * defecto), {@code dev}, {@code local}, {@code test} y {@code remote-dev}.
 * Cualquier otro perfil activo (p. ej. {@code prod}, {@code staging}) obliga a
 * configurar un secret y un keystore reales, abortando el arranque si no. Endurece
 * el compromiso de ADR-0011 ("reusar la clave dev en producción es inseguro") y la
 * revisión de seguridad. Ver ADR-0015.</p>
 */
@Component
public class IdentityStartupGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IdentityStartupGuard.class);

    /** Secret del cliente bootstrap commiteado como valor por defecto de dev. */
    private static final String DEV_BOOTSTRAP_SECRET = "changeme-local-dev";
    /** Keystore JWK commiteado en el repo (ADR-0011). */
    private static final String DEV_JWK_KEYSTORE = "classpath:keystore/dev-jwk.p12";
    /** Perfiles en los que se toleran los secretos dev. */
    private static final Set<String> DEV_PROFILES = Set.of("dev", "local", "test", "remote-dev");

    private final Environment environment;
    private final NexusOAuthBootstrapProperties bootstrapProperties;
    private final NexusOAuthJwkProperties jwkProperties;

    public IdentityStartupGuard(
            Environment environment,
            NexusOAuthBootstrapProperties bootstrapProperties,
            NexusOAuthJwkProperties jwkProperties
    ) {
        this.environment = environment;
        this.bootstrapProperties = bootstrapProperties;
        this.jwkProperties = jwkProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean devBootstrapSecret = DEV_BOOTSTRAP_SECRET.equals(bootstrapProperties.clientSecret());
        boolean devJwkKeystore = DEV_JWK_KEYSTORE.equals(jwkProperties.keystoreLocation());
        if (!devBootstrapSecret && !devJwkKeystore) {
            return; // secretos reales: nada que vigilar
        }
        if (isDevProfile()) {
            log.warn("Identity running with development OAuth secrets under a dev/default profile "
                    + "(bootstrap secret={}, jwk keystore={}). Not safe for production.",
                    devBootstrapSecret, devJwkKeystore);
            return;
        }
        throw new IllegalStateException(String.format(
                "Refusing to start: development OAuth identity secrets are configured under a non-dev "
                        + "profile (%s). Set a real NEXUS_OAUTH_BOOTSTRAP_CLIENT_SECRET and a production "
                        + "nexus.oauth.jwk.keystore-* before running outside dev "
                        + "(bootstrap secret default in use=%b, dev jwk keystore in use=%b).",
                Arrays.toString(environment.getActiveProfiles()), devBootstrapSecret, devJwkKeystore));
    }

    private boolean isDevProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return true; // perfil por defecto (vacío): se tolera (dev local + tests)
        }
        return Arrays.stream(active).anyMatch(DEV_PROFILES::contains);
    }
}
