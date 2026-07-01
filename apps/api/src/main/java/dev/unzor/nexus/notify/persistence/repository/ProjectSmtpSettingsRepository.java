package dev.unzor.nexus.notify.persistence.repository;

import dev.unzor.nexus.notify.domain.entity.ProjectSmtpSettings;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectSmtpSettingsRepository extends Repository<ProjectSmtpSettings, UUID> {

    Optional<ProjectSmtpSettings> findByProjectId(UUID projectId);

    ProjectSmtpSettings save(ProjectSmtpSettings settings);

    void deleteByProjectId(UUID projectId);
}
