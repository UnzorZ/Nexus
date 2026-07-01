package dev.unzor.nexus.notify.persistence.repository;

import dev.unzor.nexus.notify.domain.entity.NotificationTemplate;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends Repository<NotificationTemplate, UUID> {
    NotificationTemplate save(NotificationTemplate template);
    NotificationTemplate saveAndFlush(NotificationTemplate template);
    List<NotificationTemplate> findAllByProjectId(UUID projectId);
    Optional<NotificationTemplate> findByProjectIdAndId(UUID projectId, UUID id);
    Optional<NotificationTemplate> findByProjectIdAndName(UUID projectId, String name);
    boolean existsByProjectIdAndName(UUID projectId, String name);
    void delete(NotificationTemplate template);
}
