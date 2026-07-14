package dev.unzor.nexus.modules.application.service;

import dev.unzor.nexus.instance.application.service.InstanceSettingsService;
import dev.unzor.nexus.modules.api.dto.ProjectModuleStatus;
import dev.unzor.nexus.modules.domain.entity.ProjectModule;
import dev.unzor.nexus.modules.domain.enums.NexusModule;
import dev.unzor.nexus.modules.persistence.repository.ProjectModuleRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orquesta la lectura y actualización del registro de módulos por proyecto.
 *
 * <p>Los módulos sin fila persistida heredan el valor por defecto: el del
 * operador (configuración de instancia) si está definido, si no el del catálogo.</p>
 */
@Service
public class ProjectModuleService {

    private final ProjectModuleRepository projectModuleRepository;
    private final ProjectLookupService projectLookupService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final InstanceSettingsService instanceSettings;

    public ProjectModuleService(
            ProjectModuleRepository projectModuleRepository,
            ProjectLookupService projectLookupService,
            PlatformTransactionManager transactionManager,
            ApplicationEventPublisher eventPublisher,
            InstanceSettingsService instanceSettings
    ) {
        this.projectModuleRepository = projectModuleRepository;
        this.projectLookupService = projectLookupService;
        // Una TransactionTemplate propia: cada execute() corre en su transacción, de
        // modo que un reintento tras una violación de restricción única no hereda una
        // transacción ya marcada como rollback-only.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.eventPublisher = eventPublisher;
        this.instanceSettings = instanceSettings;
    }

    @Transactional(readOnly = true)
    public List<ProjectModuleStatus> listForProject(UUID projectId) {
        projectLookupService.requireById(projectId);

        Map<NexusModule, Boolean> stored = projectModuleRepository.findAllByProjectId(projectId).stream()
                .collect(Collectors.toMap(
                        ProjectModule::getModule,
                        ProjectModule::isEnabled,
                        (left, right) -> right
                ));

        // Carga los defaults del operador UNA sola vez (antes se llamaba a
        // instanceSettings.defaultModuleKeys() dos veces por módulo, y el default se
        // evaluaba eager dentro de getOrDefault aunque el módulo tuviera fila).
        Optional<Set<String>> configuredDefaults = instanceSettings.defaultModuleKeys();
        return Arrays.stream(NexusModule.values())
                .map(module -> {
                    boolean effectiveDefault = effectiveDefault(module, configuredDefaults);
                    boolean enabled = stored.getOrDefault(module, effectiveDefault);
                    return new ProjectModuleStatus(module.key(), enabled, effectiveDefault);
                })
                .toList();
    }

    /**
     * Consulta de runtime para el module gate: ¿está el módulo habilitado para el
     * proyecto? Una fila ausente hereda el valor por defecto efectivo (instancia o
     * catálogo).
     *
     * <p>A diferencia de {@link #listForProject}, no verifica la existencia del
     * proyecto (el controlador ya devolverá 404 para uno inexistente); el gate
     * sólo necesita saber el estado del módulo, en una sola lectura.</p>
     */
    @Transactional(readOnly = true)
    public boolean isEnabled(UUID projectId, NexusModule module) {
        return projectModuleRepository.findByProjectIdAndModule(projectId, module)
                .map(ProjectModule::isEnabled)
                .orElseGet(() -> effectiveDefault(module));
    }

    /**
     * Default efectivo de un módulo: el del operador (configuración de instancia)
     * si la ha definido, si no el del catálogo ({@link NexusModule#enabledByDefault()}).
     */
    private boolean effectiveDefault(NexusModule module) {
        return effectiveDefault(module, instanceSettings.defaultModuleKeys());
    }

    /** Igual que {@link #effectiveDefault(NexusModule)} reutilizando los defaults ya cargados. */
    private boolean effectiveDefault(NexusModule module, Optional<Set<String>> configuredDefaults) {
        return configuredDefaults.map(keys -> keys.contains(module.key()))
                .orElseGet(module::enabledByDefault);
    }

    /**
     * Persiste el estado de un módulo (upsert).
     *
     * <p>Verifica primero la existencia del proyecto (consistencia con
     * {@link #listForProject}: 404 en vez de 403/500 para un proyecto inexistente).
     * La upsert (find → modify → save) corre en su propia transacción y se reintenta
     * una vez si una petición concurrente insertó la fila justo antes y ganó la
     * carrera (violación de {@code uk_project_module_project_module}); en el reintento
     * la fila ya existe y se actualiza. Sin una transacción nueva por intento, la
     * condición de carrera se traduciría en un 500 para el cliente.</p>
     */
    public ProjectModuleStatus setEnabled(UUID projectId, NexusModule module, boolean enabled,
                                          UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectModuleStatus status;
        try {
            status = applySetEnabled(projectId, module, enabled);
        } catch (DataIntegrityViolationException raceLost) {
            // Inserción concurrente: en el reintento la fila ya existe.
            status = applySetEnabled(projectId, module, enabled);
        }
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, enabled ? "module.enabled" : "module.disabled", "module", module.key(),
                actorAccountId, Map.of("module", module.key())));
        return status;
    }

    private ProjectModuleStatus applySetEnabled(UUID projectId, NexusModule module, boolean enabled) {
        return transactionTemplate.execute(status -> {
            ProjectModule row = projectModuleRepository.findByProjectIdAndModule(projectId, module)
                    .orElseGet(() -> new ProjectModule(projectId, module, enabled));
            row.setEnabled(enabled);
            projectModuleRepository.save(row);
            return new ProjectModuleStatus(module.key(), enabled, effectiveDefault(module));
        });
    }
}
