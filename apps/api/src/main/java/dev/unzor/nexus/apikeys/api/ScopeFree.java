package dev.unzor.nexus.apikeys.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un endpoint del API de proyecto ({@code /api/v1/**}) como <b>abierto a
 * cualquier API key autenticada</b>, sin requerir un scope concreto. Es el opt-out
 * explícito al régimen <i>deny-by-default</i> del {@code RequiredScopeInterceptor}:
 * un endpoint SIN {@link RequiredScope} NI {@code @ScopeFree} se deniega (403).
 *
 * <p>Reservado para endpoints de introspección que cualquier key válida puede
 * llamar (p. ej. {@code GET /api/v1/whoami}). No relaja la autenticación: la key
 * sigue teniendo que resolver y su proyecto estar operativo.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ScopeFree {
}
