package dev.unzor.nexus.identity.application.configuration;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Configuración central de seguridad del módulo Identity.
 * <p>
 * Nexus registra varias {@link SecurityFilterChain} con {@link Order} explícito.
 * Spring Security las evalúa en orden; la primera cadena cuyo {@code securityMatcher}
 * coincide con la petición es la que la procesa. Esta clase define las cadenas 1 y 3;
 * la cadena 2 vive en {@link dev.unzor.nexus.shared.security.SecurityConfiguration}
 * para {@code /internal/**} y {@code /actuator/**}.
 * <p>
 * También declara los beans OAuth2/OIDC ({@link RegisteredClientRepository},
 * {@link JWKSource}, etc.). En el MVP todo es en memoria y se reinicia al arrancar
 * la aplicación.
 *
 * @see dev.unzor.nexus.shared.security.SecurityConfiguration
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Cadena de filtros del Authorization Server ({@code @Order(1)}).
     * <p>
     * Solo aplica a los endpoints del Authorization Server OAuth2 (por ejemplo
     * {@code /oauth2/authorize}, {@code /oauth2/token}, {@code /oauth2/jwks},
     * {@code /userinfo}). Activa OpenID Connect 1.0 y exige un sujeto autenticado
     * antes de emitir códigos o tokens.
     * <p>
     * Las peticiones de navegador ({@code Accept: text/html}) sin autenticación se
     * redirigen a {@code /login} para que el usuario inicie sesión y continúe el flujo
     * Authorization Code.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
            throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, (authorizationServer) ->
                        authorizationServer
                                .oidc(Customizer.withDefaults())
                )
                .authorizeHttpRequests((authorize) ->
                        authorize
                                .anyRequest().authenticated()
                )
                .exceptionHandling((exceptions) -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                );

        return http.build();
    }

    /**
     * Cadena de filtros por defecto de la aplicación ({@code @Order(3)}).
     * <p>
     * Cubre las peticiones que no gestionan la cadena del Authorization Server ni la
     * cadena compartida de internos/actuator. Las rutas públicas se limitan a la página
     * de login, sus assets estáticos, metadatos well-known de la aplicación y la página
     * de error.
     * <p>
     * El resto de rutas exigen autenticación mediante form login. La página de login la
     * sirve {@link dev.unzor.nexus.identity.api.controller.LoginController} en {@code /login}.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) {
        http
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers(
                                "/login",
                                "/identity/login.css",
                                "/.well-known/appspecific/**",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .permitAll());

        return http.build();
    }

    /**
     * Almacén temporal de usuarios en memoria para desarrollo local y pruebas del MVP OIDC.
     * Sustituir por una implementación respaldada en base de datos cuando existan usuarios
     * aislados por proyecto.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails userDetails = User.withDefaultPasswordEncoder()
                .username("user")
                .password("password")
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(userDetails);
    }

    /**
     * Registra el cliente OIDC usado por el frontend de administración de Nexus (Next.js).
     * <p>
     * Las redirect URI y post-logout URI se construyen a partir de
     * {@code nexus.frontend-base-url}. Grant types: authorization code y refresh token.
     * Se exige pantalla de consentimiento antes de conceder scopes.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(
            @Value("${nexus.frontend-base-url:http://127.0.0.1:3000}") String frontendBaseUrl
    ) {
        String normalizedFrontendBaseUrl = normalizeBaseUrl(frontendBaseUrl);

        RegisteredClient oidcClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("oidc-client")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(normalizedFrontendBaseUrl + "/login/oauth2/code/oidc-client")
                .postLogoutRedirectUri(normalizedFrontendBaseUrl + "/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .build();

        return new InMemoryRegisteredClientRepository(oidcClient);
    }

    /**
     * Claves de firma para access tokens e ID tokens.
     * <p>
     * Se genera un nuevo par RSA-2048 en cada arranque. Los tokens emitidos antes de un
     * reinicio dejan de ser verificables hasta que los clientes obtengan el JWKS actualizado.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    private static KeyPair generateRsaKey() {
        KeyPair keyPair;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return keyPair;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    /**
     * Decodifica JWT firmados por {@link #jwkSource()} para que el Authorization Server
     * pueda validar sus propios tokens cuando sea necesario.
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * Rutas por defecto del Authorization Server y metadatos del issuer.
     * Aquí se podrán configurar issuers o prefijos de ruta personalizados cuando Nexus
     * soporte issuers por proyecto.
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

}
