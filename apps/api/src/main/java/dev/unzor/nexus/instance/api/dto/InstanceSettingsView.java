package dev.unzor.nexus.instance.api.dto;

import dev.unzor.nexus.instance.domain.entity.InstanceSettings;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Valores writeable de configuración de instancia (panel del operador).
 *
 * @param registrationOpen política de registro (abierta/cerrada).
 * @param defaultModules   claves activadas por defecto en proyectos nuevos;
 *                         {@code null} = usar los defaults del catálogo (enum).
 * @param heartbeat        defaults de heartbeat de instancia; {@code null} en
 *                         interval/timeout = sin override (usar env).
 * @param updatedAt        última modificación.
 */
public record InstanceSettingsView(
        boolean registrationOpen,
        List<String> defaultModules,
        HeartbeatDefaults heartbeat,
        Instant updatedAt
) {

    public record HeartbeatDefaults(Integer intervalSeconds, Integer timeoutSeconds) {
    }

    public static InstanceSettingsView from(InstanceSettings settings) {
        List<String> modules = settings.getDefaultModules() == null
                ? null
                : List.copyOf(parseCsv(settings.getDefaultModules()));
        return new InstanceSettingsView(
                settings.isRegistrationOpen(),
                modules,
                new HeartbeatDefaults(settings.getHeartbeatIntervalSeconds(), settings.getHeartbeatTimeoutSeconds()),
                settings.getUpdatedAt());
    }

    /** Split de csv -> set ordenado de claves (minúsculas, sin blanks). */
    public static Set<String> parseCsv(String csv) {
        Set<String> keys = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                keys.add(trimmed.toLowerCase());
            }
        }
        return keys;
    }

    /** Join de claves -> csv. */
    public static String toCsv(List<String> keys) {
        if (keys == null) {
            return null;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String key : keys) {
            String trimmed = key == null ? "" : key.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed.toLowerCase());
            }
        }
        return String.join(",", unique);
    }
}
