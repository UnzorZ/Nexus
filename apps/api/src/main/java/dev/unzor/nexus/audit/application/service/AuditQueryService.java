package dev.unzor.nexus.audit.application.service;

import dev.unzor.nexus.audit.api.dto.AuditEventView;
import dev.unzor.nexus.audit.domain.entity.AuditLogEntry;
import dev.unzor.nexus.audit.persistence.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lectura del log de auditoría para el panel. Acota por {@code projectId},
 * opcionalmente por {@code since}, y devuelve los eventos más recientes hasta
 * {@code limit} (acotado a {@link #MAX_LIMIT}). El filtrado fino
 * (outcome/actor/búsqueda) se hace en el cliente sobre este conjunto acotado.
 */
@Service
public class AuditQueryService {

    static final int DEFAULT_LIMIT = 200;
    static final int MAX_LIMIT = 500;

    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> listForProject(UUID projectId, Instant since, Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        Pageable page = PageRequest.of(0, safeLimit);
        List<AuditLogEntry> entries = since == null
                ? repository.findByProjectIdOrderByOccurredAtDescIdDesc(projectId, page)
                : repository.findByProjectAndSince(projectId, since, page);
        return entries.stream()
                .map(AuditEventView::from)
                .toList();
    }
}
