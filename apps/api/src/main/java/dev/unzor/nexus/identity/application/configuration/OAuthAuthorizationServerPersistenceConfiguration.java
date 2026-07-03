package dev.unzor.nexus.identity.application.configuration;

import dev.unzor.nexus.identity.application.service.ProjectOauthClientToRegisteredClientMapper;
import dev.unzor.nexus.identity.persistence.CompositeRegisteredClientRepository;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

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
    RegisteredClientRepository registeredClientRepository(
            JdbcTemplate jdbcTemplate,
            ProjectOauthClientRepository projectRepository,
            ProjectOauthClientToRegisteredClientMapper mapper
    ) {
        return new CompositeRegisteredClientRepository(
                projectRepository, mapper, new JdbcRegisteredClientRepository(jdbcTemplate));
    }

    @Bean
    OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository
    ) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository
    ) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }
}
