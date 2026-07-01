package dev.unzor.nexus.config.application.service;

import dev.unzor.nexus.config.api.dto.ConfigValueSummary;
import dev.unzor.nexus.config.domain.entity.ProjectConfig;
import dev.unzor.nexus.config.domain.enums.ConfigValueType;
import dev.unzor.nexus.config.domain.exception.ConfigKeyNotFoundException;
import dev.unzor.nexus.config.domain.exception.InvalidConfigValueException;
import dev.unzor.nexus.config.persistence.repository.ProjectConfigRepository;
import dev.unzor.nexus.projects.application.service.ProjectLookupService;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Casos de uso de la configuración tipada de un proyecto. Las escrituras
 * validan el valor contra su tipo y auditan; la lectura la usa tanto el panel
 * como el API de proyecto.
 */
@Service
public class ProjectConfigService {

    private static final Set<String> KEY_UNIQUE_CONSTRAINTS = Set.of("uk_project_config_project_key");

    private final ProjectConfigRepository repository;
    private final ProjectLookupService projectLookupService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ProjectConfigService(
            ProjectConfigRepository repository,
            ProjectLookupService projectLookupService,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.projectLookupService = projectLookupService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ConfigValueSummary> listForProject(UUID projectId) {
        projectLookupService.requireById(projectId);
        return repository.findAllByProjectId(projectId).stream()
                .map(ConfigValueSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConfigValueSummary getValue(UUID projectId, String key) {
        projectLookupService.requireById(projectId);
        return repository.findByProjectIdAndKey(projectId, key)
                .map(ConfigValueSummary::from)
                .orElseThrow(() -> new ConfigKeyNotFoundException(
                        "Config key '" + key + "' not found in project " + projectId + "."));
    }

    /** Upsert (create o update) de un valor. Valida el tipo; audita {@code config.set}. */
    @Transactional
    public ConfigValueSummary upsert(UUID projectId, String key, String value, ConfigValueType valueType,
                                     UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        validateValue(value, valueType);
        Optional<ProjectConfig> existing = repository.findByProjectIdAndKey(projectId, key);
        try {
            ProjectConfig row = existing.orElseGet(() -> new ProjectConfig(projectId, key, value, valueType));
            row.rewrite(value, valueType);
            repository.saveAndFlush(row);
        } catch (DataIntegrityViolationException race) {
            if (isKeyUniqueViolation(race)) {
                ProjectConfig row = repository.findByProjectIdAndKey(projectId, key)
                        .orElseThrow(() -> race);
                row.rewrite(value, valueType);
                repository.save(row);
            } else {
                throw race;
            }
        }
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "config.set", "config", key, actorAccountId,
                Map.of("key", key, "valueType", valueType.name())));
        return repository.findByProjectIdAndKey(projectId, key)
                .map(ConfigValueSummary::from)
                .orElseThrow();
    }

    @Transactional
    public void delete(UUID projectId, String key, UUID actorAccountId) {
        projectLookupService.requireById(projectId);
        ProjectConfig row = repository.findByProjectIdAndKey(projectId, key)
                .orElseThrow(() -> new ConfigKeyNotFoundException(
                        "Config key '" + key + "' not found in project " + projectId + "."));
        repository.delete(row);
        eventPublisher.publishEvent(AuditEvent.byAccount(
                projectId, "config.deleted", "config", key, actorAccountId, Map.of("key", key)));
    }

    private void validateValue(String value, ConfigValueType type) {
        try {
            switch (type) {
                case STRING -> { /* cualquier texto es válido */ }
                case NUMBER -> Double.parseDouble(value);
                case BOOLEAN -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        throw new InvalidConfigValueException("value: must be 'true' or 'false' for BOOLEAN.");
                    }
                }
                case JSON -> objectMapper.readTree(value);
            }
        } catch (InvalidConfigValueException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidConfigValueException("value: does not match type " + type + ".");
        } catch (Exception exception) {
            throw new InvalidConfigValueException("value: does not match type " + type + ".");
        }
    }

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
}
