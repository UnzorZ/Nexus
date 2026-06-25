package dev.unzor.nexus.identity.application.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ NexusOAuthBootstrapProperties.class, NexusOAuthJwkProperties.class })
class OAuthBootstrapConfiguration {
}
