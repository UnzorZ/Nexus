package dev.unzor.nexus.identity.domain;

import java.util.Locale;
import java.util.Set;

import dev.unzor.nexus.identity.domain.exception.WeakPasswordException;

/**
 * Política mínima de contraseñas de los usuarios finales, la misma en el alta y en el reset
 * (auditoría 2026-09-28, N1): entre 8 y 128 caracteres, fuera de la lista de contraseñas más
 * comunes y distinta del email. El bloqueo por intentos y el rate limit completan la defensa.
 */
public final class ProjectUserPasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    /** Contraseñas de 8+ caracteres de las listas públicas más usadas en ataques de diccionario. */
    private static final Set<String> COMMON = Set.of(
            "12345678", "123456789", "1234567890", "12345678910", "11111111", "00000000", "87654321",
            "password", "password1", "password12", "password123", "passw0rd", "p@ssw0rd", "contraseña",
            "contrasena", "qwertyuiop", "qwerty123", "qwerty12", "1q2w3e4r", "1q2w3e4r5t", "iloveyou",
            "sunshine", "princess", "football", "baseball", "welcome1", "welcome123", "abc12345",
            "abcd1234", "aa123456", "asdfghjk", "zxcvbnm1", "superman", "trustno1", "letmein1",
            "admin123", "administrator", "changeme", "12341234", "123123123", "qazwsxedc");

    private ProjectUserPasswordPolicy() {
    }

    /** @throws WeakPasswordException cuando {@code password} no cumple la política */
    public static void check(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new WeakPasswordException();
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            throw new WeakPasswordException();
        }
        if (email != null && !email.isBlank()) {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            int at = normalized.indexOf('@');
            if (lower.equals(normalized) || (at > 0 && lower.equals(normalized.substring(0, at)))) {
                throw new WeakPasswordException();
            }
        }
    }
}
