package dev.unzor.nexus.identity.application.configuration;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.unzor.nexus.identity.application.observability.NexusOAuthJwkState;
import dev.unzor.nexus.identity.application.service.AuthzVersionIntrospectionAuthenticationProvider;
import dev.unzor.nexus.identity.application.service.ProjectSlugResolver;
import dev.unzor.nexus.identity.infrastructure.security.ProjectRealmIsolationFilter;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Configuración de seguridad del Authorization Server OAuth2/OIDC y cadena residual.
 *
 * <p>No utiliza {@link dev.unzor.nexus.admin.infrastructure.security.NexusAccountUserDetailsService}.
 * El panel Nexus se autentica en una cadena separada bajo {@code /panel/**}.</p>
 *
 * @see dev.unzor.nexus.admin.application.configuration.PanelSecurityConfiguration
 * @see dev.unzor.nexus.shared.security.SecurityConfiguration
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Cadena de seguridad del Authorization Server OAuth2/OIDC.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            ProjectOauthAuthenticationEntryPoint htmlEntryPoint,
            OAuth2AuthorizationService authorizationService,
            ProjectUserRepository projectUserRepository,
            ProjectSlugResolver slugResolver
    ) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                // Aislamiento de realms (remediación de auditoría, hallazgo crítico): una
                // sesión de otro realm no puede obtener un token aquí (/oauth2/authorize,
                // /token, PAR, device…). Anclado tras SecurityContextHolderFilter para correr
                // con el principal ya cargado y ANTES de los filtros del AS — así bloquea en
                // el authorize, no después de emitir el code.
                .addFilterAfter(new ProjectRealmIsolationFilter(slugResolver), SecurityContextHolderFilter.class)
                .with(authorizationServerConfigurer, authorizationServer ->
                        authorizationServer
                                // Pantalla de consentimiento con branding (B3): SAS redirige aquí
                                // cuando un cliente requiere consentimiento. Ver ConsentController.
                                .authorizationEndpoint(ae -> ae.consentPage("/oauth2/consent"))
                                // Pushed Authorization Requests (RFC 9126): el cliente empuja los
                                // parámetros de autorización a /oauth2/par, recibe un request_uri y
                                // redirige al browser al authorize con sólo client_id + request_uri.
                                // Así los params (scopes, claims, etc.) no viajan por el URL del browser
                                // ni quedan en logs/referrer. Opcional por defecto: lo soporta el AS y lo
                                // anuncia en discovery (pushed_authorization_request_endpoint), pero no
                                // fuerza a los clientes existentes a usarlo.
                                .pushedAuthorizationRequestEndpoint(Customizer.withDefaults())
                                // Device Authorization Grant (RFC 8628): dispositivos sin browser/entrada
                                // cómoda (CLI, TV, IoT) piden un device_code en /oauth2/device_authorization,
                                // muestran un user_code + verification_uri, el usuario lo aprueba en su
                                // browser en /oauth2/device_verification (redirige a la página Next.js vía
                                // DeviceVerificationController), y el dispositivo sondea /oauth2/token hasta
                                // obtener los tokens. El cliente debe llevar el grant
                                // urn:ietf:params:oauth:grant-type:device_code.
                                .deviceAuthorizationEndpoint(Customizer.withDefaults())
                                .deviceVerificationEndpoint(dv -> dv.consentPage("/oauth2/device"))
                                // Dynamic Client Registration (RFC 7591 / OIDC): un cliente se
                                // auto-registra en /connect/register (per-issuer /p/{slug}/connect/
                                // register). openRegistrationAllowed permite el registro inicial sin
                                // Initial Access Token; las lecturas/updates posteriores se autentican
                                // con el registration_access_token. CompositeRegisteredClientRepository
                                // .save persiste el nuevo cliente en project_oauth_clients del proyecto
                                // derivado del issuer (vía AuthorizationServerContextHolder) → DCR
                                // project-scoped, encaja en el modelo multi-tenant.
                                .clientRegistrationEndpoint(cr -> cr.openRegistrationAllowed(true))
                                // Introspection con enforcement de authz_version (#22): un token
                                // cuya versión stale (tras un cambio de rol) introspecta como
                                // inactivo. Ver AuthzVersionIntrospectionAuthenticationProvider.
                                .tokenIntrospectionEndpoint(ti -> ti.authenticationProviders(providers -> {
                                    AuthenticationProvider defaultProvider = providers.get(0);
                                    providers.set(0, new AuthzVersionIntrospectionAuthenticationProvider(
                                            defaultProvider, authorizationService, projectUserRepository));
                                }))
                                .oidc(oidc -> oidc
                                        // /userinfo: el mapper por defecto de SAS filtra los claims a
                                        // EMAIL/PHONE/PROFILE según el scope, lo que dropearía `permissions`.
                                        // Partimos de los claims del token (ID si existe, si no access — ambos
                                        // llevan `permissions` vía ProjectIdTokenCustomizer) y los exponemos
                                        // sin filtrar, de modo que /userinfo incluye sub, project_id,
                                        // authz_version y permissions.
                                        .userInfoEndpoint(userInfo -> userInfo.userInfoMapper(ctx -> {
                                            OAuth2Authorization az = ctx.getAuthorization();
                                            OAuth2Authorization.Token<?> idToken = az.getToken(OidcIdToken.class);
                                            OAuth2Authorization.Token<?> token = idToken != null
                                                    ? idToken : az.getAccessToken();
                                            Map<String, Object> claims = new HashMap<>(token.getClaims());
                                            return new OidcUserInfo(claims);
                                        })))
                )
                .authorizeHttpRequests(authorize -> authorize
                        // DCR (RFC 7591): el registro inicial es abierto (openRegistrationAllowed);
                        // lecturas/updates posteriores se autentican con el registration_access_token
                        // (Bearer, validado por el filtro del AS). Sin este permitAll, /oauth2/register
                        // requeriría sesión y el AS devolvería 302/401.
                        .requestMatchers(
                                "/oauth2/register/**",
                                "/p/*/oauth2/register/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                htmlEntryPoint,
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new MediaTypeRequestMatcher(MediaType.APPLICATION_JSON)
                        )
                );

        return http.build();
    }

    /**
     * Cadena de seguridad residual ({@code @Order(5)}) para recursos públicos que no
     * pertenecen al panel ni a rutas reservadas por proyecto.
     */
    @Bean
    @Order(5)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/identity/**",
                                "/oauth2/consent",
                                "/oauth2/authentication-required",
                                "/oauth2/bootstrap/callback",
                                "/.well-known/appspecific/**",
                                "/error"
                        ).permitAll()
                        .anyRequest().denyAll()
                );

        return http.build();
    }

    /**
     * Clave de firma para access tokens e ID tokens.
     *
     * <p>Se carga del alias activo ({@code nexus.oauth.jwk.key-alias}) del keystore
     * PKCS12 configurado ({@code nexus.oauth.jwk.*}), de forma que sobrevive a
     * reinicios y se comparte entre instancias. Si no se configura keystore, se genera
     * una clave efímera en memoria con una advertencia y se marca el estado efímero
     * para que la readiness lo refleje (ver ADR-0011 y
     * {@code JwkSigningKeyHealthIndicator}).</p>
     *
     * <p>El {@code JWKSet} expone una sola clave porque el {@code NimbusJwtEncoder}
     * del framework se niega a firmar cuando hay varias claves sin un selector
     * custom: la rotación con solapamiento queda fuera del alcance actual (ver
     * ADR-0011).</p>
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(
            NexusOAuthJwkProperties jwkProperties,
            ResourceLoader resourceLoader,
            NexusOAuthJwkState jwkState
    ) {
        RSAKey rsaKey = loadActiveKey(jwkProperties, resourceLoader, jwkState);
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    /**
     * Carga la clave RSA del alias activo del keystore configurado o, si no hay
     * keystore, genera una clave efímera en memoria (marcando el estado y registrando
     * una advertencia).
     */
    private static RSAKey loadActiveKey(
            NexusOAuthJwkProperties properties,
            ResourceLoader resourceLoader,
            NexusOAuthJwkState jwkState
    ) {
        String location = properties.keystoreLocation();
        if (location == null || location.isBlank()) {
            jwkState.markEphemeral();
            log.warn("No JWK keystore configured (nexus.oauth.jwk.keystore-location). Generating "
                    + "an ephemeral signing key in memory: tokens will not survive restarts and this "
                    + "setup is not valid for production or multi-instance deployments.");
            return generateEphemeralKey();
        }

        String keystorePassword = properties.keystorePassword();
        String activeAlias = properties.keyAlias();
        String keyPassword = properties.keyPassword();
        if (keystorePassword == null || activeAlias == null || keyPassword == null) {
            throw new IllegalStateException(
                    "Configured JWK keystore requires nexus.oauth.jwk.keystore-password, "
                            + "nexus.oauth.jwk.key-alias and nexus.oauth.jwk.key-password."
            );
        }

        Resource keystore = resourceLoader.getResource(toResourceLocation(location));
        if (!keystore.exists()) {
            throw new IllegalStateException(
                    "Configured JWK keystore does not exist or is not readable: " + location
            );
        }
        return loadKeyFromKeystore(keystore, keystorePassword, activeAlias, keyPassword);
    }

    /**
     * Normaliza la ubicación del keystore para que los paths absolutos sin prefijo
     * (p. ej. {@code /etc/nexus/keystore.p12}) se resuelvan como {@code file:} en
     * vez de como classpath, que es el comportamiento por defecto de
     * {@code DefaultResourceLoader}. Los valores con prefijo explícito
     * ({@code file:}, {@code classpath:}, etc.) se respetan tal cual.
     */
    private static String toResourceLocation(String location) {
        if (location.startsWith("/") && !location.contains(":")) {
            return "file:" + location;
        }
        return location;
    }

    /**
     * Carga la clave privada y el certificado del {@code alias} indicado del keystore
     * PKCS12 y construye la {@link RSAKey} con un {@code kid} estable.
     */
    public static RSAKey loadKeyFromKeystore(
            Resource keystore,
            String keystorePassword,
            String alias,
            String keyPassword
    ) {
        try (InputStream in = keystore.getInputStream()) {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(in, keystorePassword.toCharArray());
            PrivateKey privateKey = (PrivateKey) store.getKey(alias, keyPassword.toCharArray());
            Certificate certificate = store.getCertificate(alias);
            if (privateKey == null || certificate == null) {
                throw new IllegalStateException(
                        "JWK keystore has no key/certificate for alias: " + alias
                );
            }
            return rsaKey((RSAPublicKey) certificate.getPublicKey(), privateKey);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load JWK keystore: " + keystore, exception);
        }
    }

    private static RSAKey rsaKey(RSAPublicKey publicKey, PrivateKey privateKey) {
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(stableKeyId(publicKey))
                .build();
    }

    private static RSAKey generateEphemeralKey() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        return rsaKey(publicKey, privateKey);
    }

    /**
     * Identificador de clave estable y determinista (no aleatorio), derivado de la
     * propia clave pública, de modo que el JWKS expuesto y el {@code kid} de los
     * tokens sean coherentes mientras la clave no cambie.
     */
    private static String stableKeyId(PublicKey publicKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return "nexus-" + HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        }
        catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Decodifica JWT firmados por {@link #jwkSource} para que el Authorization Server
     * pueda validar sus propios tokens cuando sea necesario.
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * Rutas por defecto del Authorization Server y metadatos del issuer.
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        // multi-issuer: SAS resuelve el issuer por-request desde el path
        // (/p/{slug}/oauth2/* => issuer {origin}/p/{slug}); el endpointsMatcher
        // se auto-ensancha a /**/oauth2/** + /**/.well-known/** (B2, spec §15.3).
        return AuthorizationServerSettings.builder().multipleIssuersAllowed(true).build();
    }
}
