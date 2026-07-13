package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.identity.application.service.ProjectOauthClientToRegisteredClientMapper;
import dev.unzor.nexus.identity.infrastructure.security.ProjectUserPrincipal;
import dev.unzor.nexus.identity.persistence.CompositeRegisteredClientRepository;
import dev.unzor.nexus.identity.persistence.ProjectOperationalOAuth2AuthorizationService;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.projects.application.service.ResolveProjectBySlugService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.json.JsonMapper;

@Configuration
class OAuthAuthorizationServerPersistenceConfiguration {

    /**
     * Repositorio compuesto de clientes: clientes OAuth por proyecto
     * (project_oauth_clients) + cliente técnico global (oauth2_registered_client,
     * reconciliado por OidcRegisteredClientBootstrap). El JDBC global vive dentro
     * del composite; las autorizaciones/consent siguen usando los servicios JDBC
     * default (keyean por registered_client_id + principal_name).
     */
    @Bean
    CompositeRegisteredClientRepository registeredClientRepository(
            JdbcTemplate jdbcTemplate,
            ProjectOauthClientRepository projectRepository,
            ProjectOauthClientToRegisteredClientMapper mapper,
            ResolveProjectBySlugService slugResolver,
            ProjectLookupService projectLookupService
    ) {
        return new CompositeRegisteredClientRepository(
                projectRepository, mapper, new JdbcRegisteredClientRepository(jdbcTemplate),
                jdbcTemplate, slugResolver, projectLookupService);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate,
            CompositeRegisteredClientRepository registeredClientRepository,
            ProjectOauthClientRepository projectOauthClientRepository,
            ProjectLookupService projectLookupService
    ) {
        // Existing grants must be hydratable even after their project becomes inactive.
        // The lifecycle gate runs immediately after hydration in the decorator below;
        // making the composite return null during the row mapping would surface as a
        // DataRetrievalFailureException (HTTP 500) instead of a native OAuth rejection.
        RegisteredClientRepository hydrationRepository = new RegisteredClientRepository() {
            @Override
            public void save(RegisteredClient registeredClient) {
                registeredClientRepository.save(registeredClient);
            }

            @Override
            public RegisteredClient findById(String id) {
                return registeredClientRepository.findByIdForAuthorizationHydration(id);
            }

            @Override
            public RegisteredClient findByClientId(String clientId) {
                return registeredClientRepository.findByClientId(clientId);
            }
        };
        JdbcOAuth2AuthorizationService service = new JdbcOAuth2AuthorizationService(
                jdbcTemplate, hydrationRepository);
        // El row-mapper JDBC por defecto usa SecurityJacksonModules, cuyo
        // PolymorphicTypeValidator sólo permite tipos de Spring Security. SAS persiste en
        // oauth2_authorization: (1) el principal autenticado (ProjectUserPrincipal) y (2) los
        // claims de los tokens emitidos (metadata.token.claims), que con default-typing NON_FINAL
        // quedan tipados con @class. Al recargar la autorización (consent grant, token exchange,
        // introspection vía findByToken) Jackson 3 rechaza con "BasicPolymorphicTypeValidator
        // denied resolution" -> 500. Ampliamos el validador para los tipos que aparecen en
        // nuestros claims/principal. setAuthorizationRowMapper sólo afecta a la lectura; la
        // escritura ya emite el @class correctamente.
        BasicPolymorphicTypeValidator.Builder typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(ProjectUserPrincipal.class)
                .allowIfSubType(Number.class)      // authz_version (Long) y futuros claims numéricos
                .allowIfSubType(Boolean.class)
                .allowIfSubType(java.net.URL.class)  // iss queda tipado como URL por el AS
                .allowIfSubType(java.net.URI.class)
                .allowIfSubType(java.util.UUID.class);
        JsonMapper jsonMapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(
                        JdbcOAuth2AuthorizationService.class.getClassLoader(), typeValidator))
                .build();
        service.setAuthorizationRowMapper(
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(
                        hydrationRepository, jsonMapper));
        return new ProjectOperationalOAuth2AuthorizationService(
                service, projectOauthClientRepository, projectLookupService);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository
    ) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    /**
     * Cliente HTTP para el fan-out del back-channel logout (OIDC RFC 8417). Definido
     * explícitamente porque este contexto no autoconfigura un {@link RestClient} (inyectar
     * {@code RestClient.Builder} rompía el contexto entero). Inyectado en
     * {@link dev.unzor.nexus.identity.application.service.BackChannelLogoutService} para que su
     * retry/backoff sea testeable con un cliente mockeado.
     */
    @Bean
    RestClient backChannelLogoutRestClient() {
        return RestClient.builder().build();
    }
}
