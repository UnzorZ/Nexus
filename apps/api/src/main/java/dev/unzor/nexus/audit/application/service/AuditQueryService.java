package dev.unzor.nexus.audit.application.service;

import dev.unzor.nexus.admin.directory.AccountDirectory;
import dev.unzor.nexus.admin.directory.AccountSummary;
import dev.unzor.nexus.audit.api.dto.AuditEventView;
import dev.unzor.nexus.audit.domain.entity.AuditLogEntry;
import dev.unzor.nexus.audit.persistence.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lectura del log de auditoría para el panel. Acota por {@code projectId},
 * opcionalmente por {@code since}, y devuelve los eventos más recientes hasta
 * {@code limit} (acotado a {@link #MAX_LIMIT}). El filtrado fino
 * (outcome/actor/módulo/búsqueda) se hace en el cliente sobre este conjunto
 * acotado.
 *
 * <p>Los actores que son cuentas Nexus se enriquecen con su {@code displayName}
 * y email (resueltos en bloque vía {@link AccountDirectory} para evitar N+1),
 * de modo que el panel muestre el usuario y no el UUID crudo.</p>
 */
@Service
public class AuditQueryService {

    static final int DEFAULT_LIMIT = 200;
    static final int MAX_LIMIT = 500;
    private static final String ACTOR_ACCOUNT = "NEXUS_ACCOUNT";

    private final AuditLogRepository repository;
    private final AccountDirectory accountDirectory;

    public AuditQueryService(AuditLogRepository repository, AccountDirectory accountDirectory) {
        this.repository = repository;
        this.accountDirectory = accountDirectory;
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> listForProject(UUID projectId, Instant since, Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        Pageable page = PageRequest.of(0, safeLimit);
        List<AuditLogEntry> entries = since == null
                ? repository.findByProjectIdOrderByOccurredAtDescIdDesc(projectId, page)
                : repository.findByProjectAndSince(projectId, since, page);
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<UUID, AccountSummary> accounts = accountDirectory.findAllById(actorAccountIds(entries));
        return entries.stream()
                .map(entry -> {
                    AccountSummary account = tryParseUuid(entry.getActorId())
                            .map(accounts::get)
                            .orElse(null);
                    return AuditEventView.from(
                            entry,
                            account != null ? account.displayName() : null,
                            account != null ? account.email() : null);
                })
                .toList();
    }

    /** IDs de cuenta de los actores NEXUS_ACCOUNT del lote (para resolver en una sola consulta). */
    private Set<UUID> actorAccountIds(List<AuditLogEntry> entries) {
        return entries.stream()
                .filter(e -> ACTOR_ACCOUNT.equals(e.getActorType()))
                .map(e -> tryParseUuid(e.getActorId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<UUID> tryParseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}

