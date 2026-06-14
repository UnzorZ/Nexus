package dev.unzor.nexus.identity.application.context;

import java.util.UUID;

public record ProjectAuthenticationContext(UUID projectId, String projectSlug) {
}
