package dev.unzor.nexus.audit.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Página de auditoría con una forma ESTABLE e independiente de la serialización
 * de {@link Page} de Spring (que cambia entre versiones). El panel la usa para
 * paginar con "cargar más": {@code items} + metadatos de paginación.
 */
public record AuditPage(
        List<AuditEventView> items,
        int page,
        int size,
        int totalPages,
        long totalElements,
        boolean last
) {
    public static AuditPage from(Page<AuditEventView> result) {
        return new AuditPage(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages(),
                result.getTotalElements(),
                result.isLast());
    }
}
