package dev.unzor.nexus.shared.security;

/**
 * Tier de rate-limiting. Determina el bucket (capacidad + reposición) que aplica a
 * un endpoint de auth pública.
 *
 * <ul>
 *   <li>{@link #AUTH} - login, MFA y token (defensa contra fuerza bruta: límite
 *       ajustado).</li>
 *   <li>{@link #GENERAL} - registro, verificación de email y reseteo de contraseña
 *       (defensa contra abuso/enumeración: límite más holgado).</li>
 * </ul>
 */
enum RateLimitTier {
    AUTH,
    GENERAL
}
