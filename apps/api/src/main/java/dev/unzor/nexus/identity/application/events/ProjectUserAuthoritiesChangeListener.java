package dev.unzor.nexus.identity.application.events;

import dev.unzor.nexus.identity.domain.entity.ProjectUser;
import dev.unzor.nexus.identity.persistence.repository.ProjectUserRepository;
import dev.unzor.nexus.shared.audit.ProjectUserAuthoritiesChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reacciona a un cambio en las authorities efectivas de un usuario de proyecto
 * (asignación/borrado de rol, cambio de permisos de un rol, borrado de rol)
 * incrementando su {@code authz_version}, lo que invalida los snapshots/tokens
 * calculados con la versión anterior.
 *
 * <p>{@link EventListener} síncrono dentro de la misma transacción que el
 * publicador (el módulo {@code permissions}): si el incremento falla, la
 * asignación completa se revierte — el fail-safe deseado (nunca persistir una
 * asignación sin su bump). Si el usuario ya no existe (huérfano por una carrera
 * con su borrado), no hace nada.</p>
 */
@Component
public class ProjectUserAuthoritiesChangeListener {

    private final ProjectUserRepository repository;

    public ProjectUserAuthoritiesChangeListener(ProjectUserRepository repository) {
        this.repository = repository;
    }

    @EventListener
    public void onAuthoritiesChanged(ProjectUserAuthoritiesChanged event) {
        repository.findByProjectIdAndId(event.projectId(), event.userId())
                .ifPresent(user -> {
                    user.incrementAuthzVersion();
                    repository.save(user);
                });
    }
}
