package dev.unzor.nexus.instance.application.service;

import dev.unzor.nexus.instance.api.dto.InstanceSettingsView;
import dev.unzor.nexus.instance.domain.entity.InstanceSettings;
import dev.unzor.nexus.instance.domain.exception.InvalidInstanceSettingsException;
import dev.unzor.nexus.instance.persistence.repository.InstanceSettingsRepository;
import dev.unzor.nexus.shared.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lee y guarda la configuración writeable de instancia (registro, módulos por
 * defecto, defaults de heartbeat). Singleton fila {@code id=1}; ausente = defaults.
 * Lo consumen {@code admin} (gateo de registro), {@code modules} (defaults de
 * módulos) y {@code registry} (fallback de heartbeat) — siempre lectura.
 */
@Service
public class InstanceSettingsService {

    private static final Short ID = 1;
    private static final InstanceSettingsView DEFAULTS =
            new InstanceSettingsView(true, null, new InstanceSettingsView.HeartbeatDefaults(null, null), null);

    private final InstanceSettingsRepository repository;
    private final ApplicationEventPublisher publisher;

    public InstanceSettingsService(InstanceSettingsRepository repository,
                                   ApplicationEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Transactional(readOnly = true)
    public InstanceSettingsView view() {
        return repository.findById(ID).map(InstanceSettingsView::from).orElse(DEFAULTS);
    }

    @Transactional(readOnly = true)
    public boolean isRegistrationOpen() {
        return repository.findById(ID).map(InstanceSettings::isRegistrationOpen).orElse(true);
    }

    /**
     * Claves activadas por defecto; {@link Optional#empty()} = usar los defaults
     * del catálogo (enum). Un set vacío (presente) significa "ninguno on".
     */
    @Transactional(readOnly = true)
    public Optional<Set<String>> defaultModuleKeys() {
        return repository.findById(ID)
                .map(InstanceSettings::getDefaultModules)
                .map(InstanceSettingsView::parseCsv);
    }

    /** Defaults de heartbeat de instancia; empty = sin override (usar env). */
    @Transactional(readOnly = true)
    public Optional<int[]> heartbeatDefaults() {
        return repository.findById(ID).flatMap(s -> {
            Integer interval = s.getHeartbeatIntervalSeconds();
            Integer timeout = s.getHeartbeatTimeoutSeconds();
            if (interval != null && timeout != null) {
                return Optional.of(new int[]{interval, timeout});
            }
            return Optional.empty();
        });
    }

    @Transactional
    public InstanceSettingsView setRegistrationOpen(boolean open, UUID actorAccountId) {
        InstanceSettings settings = repository.findById(ID).orElseGet(InstanceSettings::create);
        settings.setRegistrationOpen(open, actorAccountId);
        InstanceSettings saved = repository.save(settings);
        publisher.publishEvent(AuditEvent.byAccount(
                null, "instance.registration.updated", "instance_settings", "instance", actorAccountId,
                Map.of("open", open)));
        return InstanceSettingsView.from(saved);
    }

    /**
     * @param keys {@code null} = reset al catálogo del enum; una lista = el set
     *             activo (vacía = ninguno on).
     */
    @Transactional
    public InstanceSettingsView setDefaultModules(List<String> keys, UUID actorAccountId) {
        InstanceSettings settings = repository.findById(ID).orElseGet(InstanceSettings::create);
        settings.setDefaultModules(InstanceSettingsView.toCsv(keys), actorAccountId);
        InstanceSettings saved = repository.save(settings);
        int count = keys == null ? -1 : InstanceSettingsView.parseCsv(InstanceSettingsView.toCsv(keys)).size();
        publisher.publishEvent(AuditEvent.byAccount(
                null, "instance.modules_defaults.updated", "instance_settings", "instance", actorAccountId,
                Map.of("reset", keys == null, "count", Math.max(count, 0))));
        return InstanceSettingsView.from(saved);
    }

    @Transactional
    public InstanceSettingsView setHeartbeat(Integer intervalSeconds, Integer timeoutSeconds, UUID actorAccountId) {
        if ((intervalSeconds == null) != (timeoutSeconds == null)) {
            throw new InvalidInstanceSettingsException("Set both interval and timeout, or neither to clear.");
        }
        if (intervalSeconds != null && (intervalSeconds < 1 || timeoutSeconds < intervalSeconds)) {
            throw new InvalidInstanceSettingsException("Require 1 <= interval <= timeout.");
        }
        InstanceSettings settings = repository.findById(ID).orElseGet(InstanceSettings::create);
        settings.setHeartbeat(intervalSeconds, timeoutSeconds, actorAccountId);
        InstanceSettings saved = repository.save(settings);
        publisher.publishEvent(AuditEvent.byAccount(
                null, "instance.heartbeat_defaults.updated", "instance_settings", "instance", actorAccountId,
                Map.of("interval", intervalSeconds == null ? -1 : intervalSeconds,
                        "timeout", timeoutSeconds == null ? -1 : timeoutSeconds)));
        return InstanceSettingsView.from(saved);
    }
}
