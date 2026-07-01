package dev.unzor.nexus.notify.persistence.repository;

import dev.unzor.nexus.notify.domain.entity.Notification;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends Repository<Notification, UUID> {
    Notification save(Notification notification);
    List<Notification> findTop50ByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
