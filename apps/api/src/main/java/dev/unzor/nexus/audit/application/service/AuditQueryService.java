package dev.unzor.nexus.audit.application.service;

import dev.unzor.nexus.admin.directory.AccountDirectory;
import dev.unzor.nexus.admin.directory.AccountSummary;
import dev.unzor.nexus.audit.api.dto.AuditEventView;
import dev.unzor.nexus.audit.domain.entity.AuditLogEntry;
import dev.unzor.nexus.audit.persistence.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lectura paginada del log de auditoría para el panel. Acota por
 * {@code projectId}, opcionalmente por {@code since}, y pagina con
 * {@code page}/{@code size} (tamaño acotado a {@link #MAX_SIZE}); devuelve un
 * {@link Page} para que el panel sepa cuántas páginas quedan (carga bajo
 * demanda). El filtrado fino (severity/actor/módulo/búsqueda) va en el cliente
 * sobre la página cargada.
 *
 * <p>Los actores que son cuentas Nexus se enriquecen con su {@code displayName}
 * y email (resueltos en bloque vía {@link AccountDirectory} para evitar N+1),
 * de modo que el panel muestre el usuario y no el UUID crudo.</p>
 */
@Service
public class AuditQueryService {

    static final int DEFAULT_SIZE = 50;
    static final int MAX_SIZE = 100;
    private static final String ACTOR_ACCOUNT = "NEXUS_ACCOUNT";

    private final AuditLogRepository repository;
    private final AccountDirectory accountDirectory;

    public AuditQueryService(AuditLogRepository repository, AccountDirectory accountDirectory) {
        this.repository = repository;
        this.accountDirectory = accountDirectory;
    }

    @Transactional(readOnly = true)
    public Page<AuditEventView> listForProject(UUID projectId, Instant since, Integer page, Integer size) {
        int safePage = (page == null || page < 0) ? 0 : page;
        int safeSize = (size == null || size <= 0) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<AuditLogEntry> entries = since == null
                ? repository.findByProjectIdOrderByOccurredAtDescIdDesc(projectId, pageable)
                : repository.findByProjectAndSince(projectId, since, pageable);
        Map<UUID, AccountSummary> accounts = accountDirectory.findAllById(actorAccountIds(entries.getContent()));
        return entries.map(entry -> {
            AccountSummary account = tryParseUuid(entry.getActorId())
                    .map(accounts::get)
                    .orElse(null);
            return AuditEventView.from(
                    entry,
                    account != null ? account.displayName() : null,
                    account != null ? account.email() : null,
                    account != null ? account.instanceAdmin() : null);
        });
    }

    /** IDs de cuenta de los actores NEXUS_ACCOUNT de la página (para resolver en una sola consulta). */
    private Set<UUID> actorAccountIds(java.util.List<AuditLogEntry> entries) {
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
