package dev.unzor.nexus.identity.persistence;

import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.projects.domain.exception.ProjectNotFoundException;
import dev.unzor.nexus.projects.domain.exception.ProjectNotOperationalException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Serializes authorization persistence for project clients with project archival.
 *
 * <p>The shared project-row lock is acquired before the JDBC authorization write
 * and held by the surrounding transaction until that write commits. Archival takes
 * the conflicting project-row write lock before deleting grants, so neither path can
 * pass the other and leave a newly persisted grant for an archived project.</p>
 */
public class ProjectOperationalOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final ProjectOauthClientRepository projectOauthClientRepository;
    private final ProjectLookupService projectLookupService;

    public ProjectOperationalOAuth2AuthorizationService(
            OAuth2AuthorizationService delegate,
            ProjectOauthClientRepository projectOauthClientRepository,
            ProjectLookupService projectLookupService
    ) {
        this.delegate = delegate;
        this.projectOauthClientRepository = projectOauthClientRepository;
        this.projectLookupService = projectLookupService;
    }

    @Override
    @Transactional
    public void save(OAuth2Authorization authorization) {
        findProjectClient(authorization).ifPresent(client -> {
            try {
                projectLookupService.lockOperationalById(client.getProjectId());
            } catch (ProjectNotFoundException | ProjectNotOperationalException unavailableProject) {
                throw projectNotOperational(unavailableProject);
            }
        });
        delegate.save(authorization);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return ifProjectOperational(delegate.findById(id));
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return ifProjectOperational(delegate.findByToken(token, tokenType));
    }

    private OAuth2Authorization ifProjectOperational(OAuth2Authorization authorization) {
        if (authorization == null) {
            return null;
        }
        var projectClient = findProjectClient(authorization);
        if (projectClient.isEmpty()) {
            return authorization;
        }
        try {
            projectLookupService.requireOperationalById(projectClient.get().getProjectId());
            return authorization;
        } catch (ProjectNotFoundException | ProjectNotOperationalException unavailableProject) {
            return null;
        }
    }

    private Optional<ProjectOauthClient> findProjectClient(
            OAuth2Authorization authorization
    ) {
        UUID projectClientId = parseUuid(authorization.getRegisteredClientId());
        return projectClientId == null
                ? Optional.empty()
                : projectOauthClientRepository.findById(projectClientId);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException notAProjectClientId) {
            return null;
        }
    }

    private static OAuth2AuthenticationException projectNotOperational(RuntimeException cause) {
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_GRANT,
                "The project for this authorization is not operational.",
                null);
        return new OAuth2AuthenticationException(error, cause);
    }
}
