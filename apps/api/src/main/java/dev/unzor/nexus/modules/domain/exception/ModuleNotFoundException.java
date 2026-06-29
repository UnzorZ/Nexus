package dev.unzor.nexus.modules.domain.exception;

/**
 * Lanzada cuando una clave de módulo no corresponde al catálogo canónico de Nexus.
 */
public class ModuleNotFoundException extends RuntimeException {

    private final String key;

    public ModuleNotFoundException(String key) {
        super("Unknown module: " + key);
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
