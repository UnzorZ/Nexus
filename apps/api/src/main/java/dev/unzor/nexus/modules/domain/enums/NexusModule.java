package dev.unzor.nexus.modules.domain.enums;

import dev.unzor.nexus.modules.domain.exception.ModuleNotFoundException;

import java.util.EnumSet;

/**
 * Catálogo canónico de módulos configurables por proyecto.
 *
 * <p>Las claves deben coincidir exactamente con {@code MODULE_CATALOG} del panel.</p>
 */
public enum NexusModule {
    IDENTITY("identity"),
    PERMISSIONS("permissions"),
    AUDIT("audit"),
    REGISTRY("registry"),
    NOTIFY("notify"),
    STORAGE("storage"),
    VAULT("vault"),
    BACKUP("backup"),
    DOCUMENTS("documents"),
    CONFIG("config"),
    METRICS("metrics");

    private static final EnumSet<NexusModule> DEFAULT_ENABLED =
            EnumSet.of(IDENTITY, PERMISSIONS, REGISTRY, AUDIT, CONFIG, METRICS, DOCUMENTS, NOTIFY);

    private final String key;

    NexusModule(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean enabledByDefault() {
        return DEFAULT_ENABLED.contains(this);
    }

    public static NexusModule fromKey(String key) {
        for (NexusModule module : values()) {
            if (module.key.equals(key)) {
                return module;
            }
        }
        throw new ModuleNotFoundException(key);
    }
}
