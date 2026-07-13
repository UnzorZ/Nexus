package dev.unzor.nexus.identity.application.events;

import dev.unzor.nexus.identity.application.service.ProjectUserOAuthRevocationService;
import dev.unzor.nexus.identity.application.service.ProjectUserSessionService;
import dev.unzor.nexus.projects.application.ProjectArchived;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Corta todos los grants OAuth y sesiones de usuario final al archivar un proyecto.
 *
 * <p>El listener es deliberadamente síncrono: participa en la transacción del
 * archivado y deja propagar cualquier fallo de PostgreSQL o Redis, de modo que el
 * estado del proyecto y las revocaciones confirman o revierten juntos.</p>
 */
@Component
public class ProjectArchivedOAuthRevocationListener {

    private final ProjectUserOAuthRevocationService revocationService;
    private final ProjectUserSessionService sessionService;

    public ProjectArchivedOAuthRevocationListener(
            ProjectUserOAuthRevocationService revocationService,
            ProjectUserSessionService sessionService
    ) {
        this.revocationService = revocationService;
        this.sessionService = sessionService;
    }

    @EventListener
    public void onProjectArchived(ProjectArchived event) {
        revocationService.revokeForProject(event.projectId());
        sessionService.revokeAllForProject(event.projectId());
    }
}
