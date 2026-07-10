package dev.unzor.nexus.metrics.api.export;

import dev.unzor.nexus.metrics.api.dto.MetricPoint;
import dev.unzor.nexus.metrics.api.dto.MetricSeries;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusExpositionTest {

    @Test
    void emitsHelpAndTypeGaugeWithLatestValuePerTagset() {
        Instant t1 = Instant.parse("2026-07-10T10:00:00Z");
        Instant t2 = Instant.parse("2026-07-10T10:01:00Z");
        MetricSeries series = new MetricSeries("demo_requests", 7, t2, 2,
                List.of(new MetricPoint(5, Map.of(), t1), new MetricPoint(7, Map.of(), t2)));

        String out = PrometheusExposition.format(List.of(series));

        // Una sola muestra (la más reciente) por tagset, sin timestamp.
        assertThat(out).contains("# HELP demo_requests demo_requests");
        assertThat(out).contains("# TYPE demo_requests gauge");
        assertThat(out).contains("demo_requests 7.0\n");
        // El punto viejo no se emite por separado (dedupe).
        assertThat(out).doesNotContain("demo_requests 5.0");
    }

    @Test
    void mapsTagsToSortedLabelsAndEscapesValues() {
        MetricSeries series = new MetricSeries("queue.depth", 3, Instant.EPOCH, 1,
                List.of(new MetricPoint(3, Map.of("region", "eu", "env", "prod"), Instant.EPOCH)));

        String out = PrometheusExposition.format(List.of(series));

        // Labels ordenados alfabéticamente por clave.
        assertThat(out).contains("queue_depth{env=\"prod\",region=\"eu\"} 3.0");
    }

    @Test
    void sanitizesMetricAndLabelNames() {
        MetricSeries series = new MetricSeries("orders.created.total", 1, Instant.EPOCH, 1,
                List.of(new MetricPoint(1, Map.of("http.status", "200"), Instant.EPOCH)));

        String out = PrometheusExposition.format(List.of(series));

        assertThat(out).contains("# TYPE orders_created_total gauge");
        assertThat(out).contains("orders_created_total{http_status=\"200\"}");
    }

    @Test
    void escapesSpecialCharsInLabelValues() {
        MetricSeries series = new MetricSeries("m", 1, Instant.EPOCH, 1,
                List.of(new MetricPoint(1, Map.of("k", "a\"b\\c\nd"), Instant.EPOCH)));

        String out = PrometheusExposition.format(List.of(series));

        assertThat(out).contains("m{k=\"a\\\"b\\\\c\\nd\"}");
    }

    @Test
    void emptySeriesProducesEmptyBody() {
        assertThat(PrometheusExposition.format(List.of())).isEqualTo("");
        assertThat(PrometheusExposition.format(null)).isEqualTo("");
    }

    @Test
    void dedupesByTagsetKeepingLatestAcrossTagsets() {
        Instant t1 = Instant.parse("2026-07-10T10:00:00Z");
        Instant t2 = Instant.parse("2026-07-10T10:01:00Z");
        // Misma métrica, dos tagsets distintos, varios puntos cada uno.
        MetricSeries series = new MetricSeries("temp", 90, t2, 4, List.of(
                new MetricPoint(80, Map.of("host", "a"), t1),
                new MetricPoint(85, Map.of("host", "b"), t1),
                new MetricPoint(88, Map.of("host", "a"), t2),
                new MetricPoint(90, Map.of("host", "b"), t2)));

        String out = PrometheusExposition.format(List.of(series));

        // Un valor latest por host (a=88, b=90), no los viejos.
        assertThat(out).contains("temp{host=\"a\"} 88.0");
        assertThat(out).contains("temp{host=\"b\"} 90.0");
        assertThat(out).doesNotContain("temp{host=\"a\"} 80.0");
        assertThat(out).doesNotContain("temp{host=\"b\"} 85.0");
    }

    @Test
    void contentTypeIsPrometheusExpositionV004() {
        assertThat(PrometheusExposition.CONTENT_TYPE).isEqualTo("text/plain; version=0.0.4; charset=utf-8");
    }
}
