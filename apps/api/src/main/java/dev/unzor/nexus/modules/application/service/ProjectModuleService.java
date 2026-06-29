package dev.unzor.nexus.modules.application.service;

import dev.unzor.nexus.modules.api.dto.ProjectModuleStatus;
import dev.unzor.nexus.modules.domain.entity.ProjectModule;
import dev.unzor.nexus.modules.domain.enums.NexusModule;
import dev.unzor.nexus.modules.persistence.repository.ProjectModuleRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orquesta la lectura y actualización del registro de módulos por proyecto.
 *
 * <p>Los módulos sin fila persistida heredan el valor por defecto del catálogo.</p>
 */
@Service
@Transactional
public class ProjectModuleService {

    private final ProjectModuleRepository projectModuleRepository;
    private final ProjectLookupService projectLookupService;

    public ProjectModuleService(
            ProjectModuleRepository projectModuleRepository,
            ProjectLookupService projectLookupService
    ) {
        this.projectModuleRepository = projectModuleRepository;
        this.projectLookupService = projectLookupService;
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

        return Arrays.stream(NexusModule.values())
                .map(module -> new ProjectModuleStatus(
                        module.key(),
                        stored.getOrDefault(module, module.enabledByDefault()),
                        module.enabledByDefault()))
                .toList();
    }

    public ProjectModuleStatus setEnabled(UUID projectId, NexusModule module, boolean enabled) {
        ProjectModule row = projectModuleRepository.findByProjectIdAndModule(projectId, module)
                .orElseGet(() -> new ProjectModule(projectId, module, enabled));
        row.setEnabled(enabled);
        projectModuleRepository.save(row);
        return new ProjectModuleStatus(module.key(), enabled, module.enabledByDefault());
    }
}
