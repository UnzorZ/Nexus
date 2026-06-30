package dev.unzor.nexus.audit.domain.entity;

import dev.unzor.nexus.audit.domain.MetadataConverter;
import dev.unzor.nexus.shared.audit.AuditEvent;
import dev.unzor.nexus.shared.audit.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entrada inmutable del log de auditoría (tabla {@code audit_log}, ADR-0004). Se
 * crea a partir de un {@link AuditEvent} persistido por {@code AuditLogListener}
 * tras el commit de la operación auditada. {@code projectId} es nullable
 * (rechazos de auth anónimos); el resto refleja 1:1 el evento.
 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "resource_type", length = 64)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Convert(converter = MetadataConverter.class)
    @Column(name = "metadata_json")
    private Map<String, Object> metadata;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** Mapea un {@link AuditEvent} a una entrada lista para persistir. */
    public static AuditLogEntry from(AuditEvent event) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.projectId = event.projectId();
        entry.action = event.action();
        entry.resourceType = event.resourceType();
        entry.resourceId = event.resourceId();
        entry.severity = event.severity();
        entry.actorType = event.actorType();
        entry.actorId = event.actorId();
        entry.ip = event.ip();
        entry.userAgent = event.userAgent();
        entry.traceId = event.traceId();
        entry.metadata = event.metadata();
        return entry;
    }

    @PrePersist
    void onCreate() {
        occurredAt = Instant.now();
    }
}
