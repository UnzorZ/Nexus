package dev.unzor.nexus.permissions.application.service;

import dev.unzor.nexus.permissions.api.dto.PermissionDetails;
import dev.unzor.nexus.permissions.domain.entity.ProjectPermission;
import dev.unzor.nexus.permissions.domain.exception.PermissionAlreadyExistsException;
import dev.unzor.nexus.permissions.domain.exception.PermissionNotFoundException;
import dev.unzor.nexus.permissions.persistence.repository.ProjectPermissionRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    public ProjectPermissionsService(
            ProjectPermissionRepository permissionRepository,
            ProjectLookupService projectLookupService
    ) {
        this.permissionRepository = permissionRepository;
        this.projectLookupService = projectLookupService;
    }

    @Transactional(readOnly = true)
    public List<PermissionDetails> listForProject(UUID projectId) {
        projectLookupService.requireById(projectId);
        return permissionRepository.findAllByProjectId(projectId).stream()
                .map(PermissionDetails::from)
                .toList();
    }

    @Transactional
    public PermissionDetails create(UUID projectId, String key, String label, String description) {
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
    public PermissionDetails update(UUID projectId, UUID permissionId, String label, String description) {
        projectLookupService.requireById(projectId);
        ProjectPermission permission = requirePermission(projectId, permissionId);
        permission.relabel(label, description);
        return PermissionDetails.from(permissionRepository.save(permission));
    }

    @Transactional
    public void delete(UUID projectId, UUID permissionId) {
        projectLookupService.requireById(projectId);
        ProjectPermission permission = requirePermission(projectId, permissionId);
        permissionRepository.delete(permission);
        // Las concesiones rol→permiso referencian la clave (no el id), así que
        // sobreviven: borrar un permiso del catálogo no rompe los roles.
    }

    private ProjectPermission requirePermission(UUID projectId, UUID permissionId) {
        return permissionRepository.findByProjectIdAndId(projectId, permissionId)
                .orElseThrow(() -> new PermissionNotFoundException(
                        "Permission " + permissionId + " not found in project " + projectId + "."));
    }
}
