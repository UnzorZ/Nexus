package dev.unzor.nexus.notify.application.service;

import dev.unzor.nexus.notify.domain.exception.UnsafeSmtpHostException;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Bloquea hosts SMTP que resuelven a direcciones no públicas. En una instancia
 * multi-tenant el campo {@code host} lo controla el usuario, así que sin este
 * guard un proyecto malicioso podría apuntar el relay a servicios internos
 * (p. ej. {@code 169.254.169.254} metadata, {@code 127.0.0.1}, rangos privados)
 * y usar Nexus como proxy de escaneo (SSRF).
 *
 * <p>Resuelve el host y rechaza si <em>cualquier</em> dirección es no pública.
 * Residual: DNS rebinding (TTL 0 que cambia entre la comprobación y la
 * conexión); se anota en ADR-0013 como mitigación de base.
 */
final class SmtpHostGuard {

    private SmtpHostGuard() {
    }

    static void assertSafe(String host) {
        if (host == null || host.isBlank()) {
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException unresolved) {
            // Sin resolución no hay a quién conectarse; no es SSRF.
            return;
        }
        for (InetAddress address : addresses) {
            if (isNonPublic(address)) {
                throw new UnsafeSmtpHostException(
                        "SMTP host resolves to a non-public address (" + address.getHostAddress()
                                + "). Private, loopback and link-local hosts are not allowed.");
            }
        }
    }

    private static boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        // IPv6 unique-local fc00::/7 (no cubierto por isSiteLocalAddress).
        if (address instanceof Inet6Address v6 && (v6.getAddress()[0] & 0xFE) == 0xFC) {
            return true;
        }
        return false;
    }
}
