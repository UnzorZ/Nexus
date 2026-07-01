package dev.unzor.nexus.documents.persistence.repository;

import dev.unzor.nexus.documents.domain.entity.DocumentTemplate;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTemplateRepository extends Repository<DocumentTemplate, UUID> {
    DocumentTemplate save(DocumentTemplate template);
    DocumentTemplate saveAndFlush(DocumentTemplate template);
    List<DocumentTemplate> findAllByProjectId(UUID projectId);
    Optional<DocumentTemplate> findByProjectIdAndId(UUID projectId, UUID id);
    Optional<DocumentTemplate> findByProjectIdAndName(UUID projectId, String name);
    boolean existsByProjectIdAndName(UUID projectId, String name);
    void delete(DocumentTemplate template);
}
