package dev.unzor.nexus.documents.persistence.repository;

import dev.unzor.nexus.documents.domain.entity.DocumentRender;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

public interface DocumentRenderRepository extends Repository<DocumentRender, UUID> {
    DocumentRender save(DocumentRender render);
    List<DocumentRender> findTop50ByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
