package io.nexus.client;

import java.util.Collection;

/**
 * Resolución de permisos con comodines (spec §14.3). Un permiso concedido
 * satisface uno requerido si:
 * <ul>
 *   <li>coinciden exactamente, o</li>
 *   <li>el concedido es {@code *} (comodín global), o</li>
 *   <li>el concedido termina en {@code .*} (comodín de namespace) y el requerido
 *       empieza por ese namespace y es estrictamente más largo — p. ej.
 *       {@code orders.*} cubre {@code orders.read} y {@code orders.billing.export},
 *       pero no el namespace pelado {@code orders}.</li>
 * </ul>
 *
 * <p>Nexus devuelve las entradas con comodín <em>verbatim</em> en el claim
 * {@code permissions} y en el snapshot; la expansión a claves concretas se
 * resuelve aquí, en el lado del cliente.</p>
 */
public final class PermissionMatcher {

    private PermissionMatcher() {}

    public static boolean matches(Collection<String> granted, String required) {
        if (granted == null || required == null || required.isEmpty()) {
            return false;
        }
        for (String g : granted) {
            if (g == null) {
                continue;
            }
            if (g.equals(required) || "*".equals(g)) {
                return true;
            }
            if (g.endsWith(".*")) {
                // "orders.*" -> prefix "orders." ; required debe empezar por él y ser más largo.
                String prefix = g.substring(0, g.length() - 1);
                if (required.startsWith(prefix) && required.length() > prefix.length()) {
                    return true;
                }
            }
        }
        return false;
    }
}
