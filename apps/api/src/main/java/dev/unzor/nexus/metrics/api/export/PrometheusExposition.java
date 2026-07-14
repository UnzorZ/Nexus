package dev.unzor.nexus.metrics.api.export;

import dev.unzor.nexus.metrics.api.dto.MetricSeries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Formatea las series de métricas de un proyecto en <b>exposition format de
 * Prometheus</b> ({@code text/plain; version=0.0.4}), de modo que un servidor
 * Prometheus pueda scrapearlas ({@code GET /api/v1/metrics/export} con
 * {@code bearer_token} = API key).
 *
 * <p>Cada {@link MetricSeries} ya es un (nombre, conjunto de tags) distinto
 * (agrupado en el servicio, M7c2), así que emitimos <b>una muestra por serie</b>
 * —su valor más reciente ({@code lastValue})— sin timestamp, para que Prometheus
 * la estampe al scrapeo (estándar). {@code # HELP}/{@code # TYPE} se emiten una
 * sola vez por nombre (aunque varias series compartan nombre con tags distintos).
 * Como Nexus no sabe si una métrica es counter o gauge, todas se exponen como
 * {@code gauge}.</p>
 *
 * <p>Sanea nombres de métrica y de label al charset de Prometheus
 * ({@code [a-zA-Z_:][a-zA-Z0-9_:]*} / {@code [a-zA-Z_][a-zA-Z0-9_]*}) y escapa
 * los valores de label ({@code \}, {@code "}, salto de línea).</p>
 */
public final class PrometheusExposition {

    /** Content-Type de exposition format v0.0.4. */
    public static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    private PrometheusExposition() {
    }

    public static String format(List<MetricSeries> series) {
        StringBuilder out = new StringBuilder();
        if (series == null) {
            return "";
        }
        Set<String> seenNames = new HashSet<>();
        for (MetricSeries s : series) {
            String name = sanitizeMetricName(s.name());
            if (name.isBlank()) {
                continue;
            }
            // HELP/TYPE una vez por nombre, aunque haya varias series (tagsets) del mismo.
            if (seenNames.add(name)) {
                out.append("# HELP ").append(name).append(' ').append(name).append('\n');
                out.append("# TYPE ").append(name).append(" gauge\n");
            }
            out.append(name).append(labels(s.tags()))
                    .append(' ').append(formatValue(s.lastValue())).append('\n');
        }
        return out.toString();
    }

    private static String labels(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        List<String> keys = new ArrayList<>(tags.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(sanitizeLabelName(keys.get(i)))
                    .append("=\"").append(escape(tags.get(keys.get(i)))).append('"');
        }
        return sb.append('}').toString();
    }

    /** Sanea un nombre de métrica al charset {@code [a-zA-Z_:][a-zA-Z0-9_:]*}. */
    static String sanitizeMetricName(String name) {
        return sanitize(name, true);
    }

    /** Sanea un nombre de label al charset {@code [a-zA-Z_][a-zA-Z0-9_]*}. */
    static String sanitizeLabelName(String key) {
        return sanitize(key, false);
    }

    private static String sanitize(String raw, boolean allowColon) {
        String s = raw == null ? "" : raw;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'
                    || (allowColon && c == ':')
                    || (i > 0 && c >= '0' && c <= '9');
            sb.append(ok ? c : '_');
        }
        if (sb.length() == 0 || (sb.charAt(0) >= '0' && sb.charAt(0) <= '9')) {
            sb.insert(0, '_');
        }
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String formatValue(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "+Inf";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        }
        return Double.toString(value);
    }
}
