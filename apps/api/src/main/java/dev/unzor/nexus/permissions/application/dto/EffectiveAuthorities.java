package dev.unzor.nexus.permissions.application.dto;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Conjunto de claves de permiso efectivas de un usuario de proyecto: la unión
 * (de-duplicada y ordenada) de los permisos de todos los roles que tiene
 * asignados, junto con las claves de esos roles. Los comodines
 * ({@code orders.*}, {@code *}) se devuelven tal cual; su expansión queda fuera
 * del alcance actual.
 *
 * <p>Servicio publicado por el módulo {@code permissions} para que el módulo
 * {@code identity} pueda construir las authorities del {@code ProjectUser}
 * (y el snapshot de autorización del API de proyecto) sin acceder a los
 * repositorios de {@code permissions}.</p>
 */
public record EffectiveAuthorities(Set<String> permissionKeys, Set<String> roleKeys) {

    public EffectiveAuthorities {
        // SortedSet inmutable: orden determinista (estable, útil para snapshots/log)
        // y de-duplicado. Set.copyOf no preserva el orden de un TreeSet.
        permissionKeys = permissionKeys == null
                ? Collections.emptySortedSet()
                : Collections.unmodifiableSortedSet(new TreeSet<>(permissionKeys));
        roleKeys = roleKeys == null
                ? Collections.emptySortedSet()
                : Collections.unmodifiableSortedSet(new TreeSet<>(roleKeys));
    }

    /** Atajo para quien sólo necesita las claves de permiso (sin roles). */
    public EffectiveAuthorities(Set<String> permissionKeys) {
        this(permissionKeys, Collections.emptySortedSet());
    }

    public static EffectiveAuthorities empty() {
        return new EffectiveAuthorities(Collections.emptySortedSet(), Collections.emptySortedSet());
    }
}
