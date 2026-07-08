package io.nexus.example.client;

import java.util.Collection;

/**
 * Glob matcher for Nexus permission keys, per the Nexus spec §14.3 (MVP matching
 * rules, positive-only). A granted permission set covers a required key when any
 * granted entry satisfies one of:
 *
 * <ul>
 *   <li><b>exact</b> — {@code orders.read} matches {@code orders.read},</li>
 *   <li><b>namespace wildcard</b> — {@code orders.*} matches {@code orders.read}
 *       (and any key beginning with the {@code orders.} prefix, including deeper
 *       keys like {@code orders.billing.export}),</li>
 *   <li><b>global wildcard</b> — {@code *} matches everything.</li>
 * </ul>
 *
 * <p>Nexus returns wildcard entries <em>verbatim</em> in the {@code permissions}
 * claim (ADR-0003); the consumer expands them with this matcher. This is the
 * canonical reference implementation — lift it into your own resource server.</p>
 */
public final class PermissionMatcher {

    private PermissionMatcher() {
    }

    /**
     * Does the granted set of permission keys (which may include wildcards)
     * cover the required concrete key?
     */
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
                // "orders.*" -> prefix "orders." ; required must start with it and be longer.
                String prefix = g.substring(0, g.length() - 1);
                if (required.startsWith(prefix) && required.length() > prefix.length()) {
                    return true;
                }
            }
        }
        return false;
    }
}
