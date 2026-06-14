package dev.unzor.nexus.shared.validation;

import java.util.Locale;

/**
 * Normaliza direcciones de email antes de aplicar restricciones de unicidad o
 * comparaciones de autenticación.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /**
     * Recorta espacios y convierte el email a minúsculas usando {@link Locale#ROOT}.
     */
    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
