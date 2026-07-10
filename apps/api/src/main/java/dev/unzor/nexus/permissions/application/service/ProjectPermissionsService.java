package dev.unzor.nexus.permissions.application.service;

import dev.unzor.nexus.permissions.api.dto.PermissionDeclaration;
import dev.unzor.nexus.permissions.api.dto.PermissionDeclarationReceipt;
import dev.unzor.nexus.permissions.api.dto.PermissionDetails;
import dev.unzor.nexus.permissions.domain.entity.ProjectPermission;
import dev.unzor.nexus.permissions.domain.enums.PermissionSource;
import dev.unzor.nexus.permissions.domain.exception.PermissionAlreadyExistsException;
import dev.unzor.nexus.permissions.domain.exception.PermissionNotFoundException;
import dev.unzor.nexus.permissions.persistence.repository.ProjectPermissionRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Casos de uso del catálogo de permisos de un proyecto (spec §9.7). Las claves
 * las define el usuario y se validan por formato en el boundary de la API; este
 * servicio gestiona el ciclo de vida de las filas del catálogo.
 */
@Service
public class ProjectPermissionsService {

    private final ProjectPermissionRepository permissionRepository;
    private final ProjectLookupService projectLookupService;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectPermissionsService(
            ProjectPermissionRepository permissionRepository,
            ProjectLookupService projectLookupService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.permissionRepository = permissionRepository;
        this.projectLookupService = projectLookupService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<PermissionDetails> listForProject(UUID projectId) {
        projectLookupService.requireById(projectId);
        return permissionRepository.findAllByProjectId(projectId).stream()
                .map(PermissionDetails::from)
                .toList();
    }

    @Transactional
    public PermissionDetails create(UUID projectId, String key, String label, String description,
                                    UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        if (permissionRepository.existsByProjectIdAndKey(projectId, key)) {
            throw new PermissionAlreadyExistsException(
                    "A permission with key '" + key + "' already exists in this project.");
        }
        try {
            // saveAndFlush materializa el INSERT dentro del bloque para que la
            // violación de unicidad (carrera de creación concurrente) se lance
            // aquí y se traduzca a 409, no como 500 al hacer commit.
            ProjectPermission saved = permissionRepository.saveAndFlush(
                    new ProjectPermission(projectId, key, label, description));
            eventPublisher.publishEvent(AuditEvent.byAccount(
                    projectId, "permission.created", "permission", Objects.toString(saved.getId(), null),
                    actorAccountId, Map.of("key", key)));
            return PermissionDetails.from(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isKeyUniqueViolation(exception)) {
                throw new PermissionAlreadyExistsException(
                        "A permission with key '" + key + "' already exists in this project.");
            }
            throw exception;
        }
    }

    /** Restricciones de unicidad de la clave, para distinguirlas de otras
     * violaciones de integridad (NOT NULL, FK) que no son un conflicto de key. */
    private static final Set<String> KEY_UNIQUE_CONSTRAINTS =
            Set.of("uk_project_permissions_project_key");

    private static boolean isKeyUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && KEY_UNIQUE_CONSTRAINTS.contains(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Transactional
    public PermissionDetails update(UUID projectId, UUID permissionId, String label, String description,
                                    UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectPermission permission = requirePermission(projectId, permissionId);
        permission.relabel(label, description);
        PermissionDetails details = PermissionDetails.from(permissionRepository.save(permission));
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "permission.updated", "permission", permissionId.toString(),
                actorAccountId, Map.of("key", permission.getKey())));
        return details;
    }

    @Transactional
    public void delete(UUID projectId, UUID permissionId, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectPermission permission = requirePermission(projectId, permissionId);
        permissionRepository.delete(permission);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "permission.deleted", "permission", permissionId.toString(),
                actorAccountId, Map.of("key", permission.getKey())));
        // Las concesiones rol→permiso referencian la clave (no el id), así que
        // sobreviven: borrar un permiso del catálogo no rompe los roles.
    }

    /**
     * Sincronización declarativa de permisos desde una aplicación (spec §18 SDK).
     * Recibe el conjunto de permisos que la app declara usar y:
     * <ul>
     *   <li>para cada permiso declarado: actualiza su etiqueta (si trae), refresca
     *       {@code lastDeclaredAt} y limpia {@code missingFromLastSync}; si no
     *       existe, lo crea con origen {@link PermissionSource#CODE} y
     *       {@code declaredBy = app};</li>
     *   <li>si la app declara su identidad ({@code app} no vacío), marca como
     *       {@code missingFromLastSync} los permisos de ESE app que no estén en la
     *       declaración — no los de otras apps ni los gestionados a mano.</li>
     * </ul>
     * Los permisos de origen {@code WEB}/{@code SYSTEM} nunca se marcan missing
     * ni se les cambia el {@code declaredBy} (la sincronización no usurpa el
     * catálogo gestionado por el operador). Si {@code app} es nulo/vacío, no se
     * reconcilia (no se marca nada missing) — modo seguro para no clobber entre
     * apps que no declaran identidad. No bumpa {@code authz_version}.
     */
    @Transactional
    public PermissionDeclarationReceipt declare(UUID projectId, String app, List<PermissionDeclaration> declarations) {
        projectLookupService.requireById(projectId);
        Instant now = Instant.now();
        boolean scoped = app != null && !app.isBlank();
        Set<String> declaredKeys = new LinkedHashSet<>();
        int created = 0;

        List<ProjectPermission> existing = permissionRepository.findAllByProjectId(projectId);
        java.util.Map<String, ProjectPermission> byKey = new java.util.HashMap<>();
        for (ProjectPermission p : existing) {
            byKey.put(p.getKey(), p);
        }

        for (PermissionDeclaration d : declarations) {
            if (d.key() == null || d.key().isBlank()) {
                continue;
            }
            declaredKeys.add(d.key());
            ProjectPermission perm = byKey.get(d.key());
            if (perm == null) {
                ProjectPermission createdPerm = permissionRepository.save(
                        new ProjectPermission(projectId, d.key(),
                                d.label() == null || d.label().isBlank() ? d.key() : d.label(),
                                null, PermissionSource.CODE));
                createdPerm.syncDeclare(d.label(), now, scoped ? app : null);
                byKey.put(d.key(), createdPerm);
                created++;
            } else {
                perm.syncDeclare(d.label(), now, scoped ? app : null);
            }
        }

        int markedMissing = 0;
        if (scoped) {
            for (ProjectPermission p : byKey.values()) {
                if (declaredKeys.contains(p.getKey())) {
                    continue;
                }
                // Sólo los permisos que ESTA app declaró (mismo declaredBy) y dejó
                // de declarar — no los de otras apps ni los WEB/SYSTEM (declaredBy null).
                if (app.equals(p.getDeclaredBy())) {
                    p.markMissingFromSync();
                    markedMissing++;
                }
            }
        }
        for (ProjectPermission p : byKey.values()) {
            permissionRepository.save(p);
        }

        return new PermissionDeclarationReceipt(declaredKeys.size(), created, markedMissing);
    }

    private ProjectPermission requirePermission(UUID projectId, UUID permissionId) {
        return permissionRepository.findByProjectIdAndId(projectId, permissionId)
                .orElseThrow(() -> new PermissionNotFoundException(
                        "Permission " + permissionId + " not found in project " + projectId + "."));
    }
}
