package dev.unzor.nexus.identity.application.service;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Normaliza el <b>origen web</b> ({@code scheme://host[:port]}) de una URI registrada,
 * p. ej. un {@code redirect_uri} de un cliente OAuth. Permite derivar, sin
 * configuración de instancia, los orígenes CORS permitidos de un proyecto y el
 * destino del entry point de login embebido: si un proyecto registra
 * {@code https://app.example/callback}, su origen es {@code https://app.example}.
 *
 * <p>Sólo orígenes absolutos con scheme y host. Omite el puerto cuando es el por
 * defecto del scheme (80/443). Devuelve {@code null} para URIs inválidas, relativas
 * o sin host, de modo que el llamador pueda saltarlas sin propagar entradas rotas
 * a una lista de orígenes permitidos.</p>
 */
public final class WebOrigin {

    private WebOrigin() {
    }

    /**
     * @return el origen normalizado en minúsculas (scheme y host), o {@code null} si la
     *         URI no es absoluta o válida, o carece de host.
     */
    public static String of(String uri) {
        if (uri == null) {
            return null;
        }
        String trimmed = uri.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        URI parsed;
        try {
            parsed = new URI(trimmed);
        } catch (URISyntaxException ex) {
            return null;
        }
        String scheme = parsed.getScheme();
        String host = parsed.getHost();
        if (scheme == null || host == null || host.isEmpty()) {
            return null;
        }
        int port = parsed.getPort();
        StringBuilder origin = new StringBuilder()
                .append(scheme.toLowerCase())
                .append("://")
                .append(host.toLowerCase());
        if (port > 0 && !isDefaultPort(scheme, port)) {
            origin.append(':').append(port);
        }
        return origin.toString();
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }
}
