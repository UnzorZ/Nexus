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
    void emitsHelpTypeAndLatestValueForSingleSeries() {
        Instant t2 = Instant.parse("2026-07-10T10:01:00Z");
        // lastValue ya es el más reciente; el formatter no rederiva desde los puntos.
        MetricSeries series = new MetricSeries("demo_requests", Map.of(), 7, t2, 2,
                List.of(new MetricPoint(5, Map.of(), Instant.parse("2026-07-10T10:00:00Z")),
                        new MetricPoint(7, Map.of(), t2)));

        String out = PrometheusExposition.format(List.of(series));

        assertThat(out).contains("# HELP demo_requests demo_requests");
        assertThat(out).contains("# TYPE demo_requests gauge");
        assertThat(out).contains("demo_requests 7.0\n");
    }

    @Test
    void mapsTagsToSortedLabelsAndEscapesValues() {
        MetricSeries series = new MetricSeries("queue.depth", Map.of("region", "eu", "env", "prod"), 3,
                Instant.EPOCH, 1, List.of(new MetricPoint(3, Map.of("region", "eu", "env", "prod"), Instant.EPOCH)));

        String out = PrometheusExposition.format(List.of(series));

        // Labels ordenados alfabéticamente por clave.
        assertThat(out).contains("queue_depth{env=\"prod\",region=\"eu\"} 3.0");
    }

    @Test
    void sanitizesMetricAndLabelNames() {
        MetricSeries series = new MetricSeries("orders.created.total", Map.of("http.status", "200"), 1,
                Instant.EPOCH, 1, List.of(new MetricPoint(1, Map.of("http.status", "200"), Instant.EPOCH)));

        String out = PrometheusExposition.format(List.of(series));

        assertThat(out).contains("# TYPE orders_created_total gauge");
        assertThat(out).contains("orders_created_total{http_status=\"200\"}");
    }

    @Test
    void escapesSpecialCharsInLabelValues() {
        MetricSeries series = new MetricSeries("m", Map.of("k", "a\"b\\c\nd"), 1, Instant.EPOCH, 1,
                List.of(new MetricPoint(1, Map.of("k", "a\"b\\c\nd"), Instant.EPOCH)));

        String out = PrometheusExposition.format(List.of(series));

        assertThat(out).contains("m{k=\"a\\\"b\\\\c\\nd\"}");
    }

    @Test
    void multipleSeriesSameNameEmitHelpTypeOnceAndOneSamplePerTagset() {
        Instant t2 = Instant.parse("2026-07-10T10:01:00Z");
        // Mismo nombre, dos tagsets → dos series (M7c2). HELP/TYPE una sola vez por nombre.
        MetricSeries a = new MetricSeries("temp", Map.of("host", "a"), 88, t2, 2,
                List.of(new MetricPoint(80, Map.of("host", "a"), Instant.parse("2026-07-10T10:00:00Z")),
                        new MetricPoint(88, Map.of("host", "a"), t2)));
        MetricSeries b = new MetricSeries("temp", Map.of("host", "b"), 90, t2, 2,
                List.of(new MetricPoint(85, Map.of("host", "b"), Instant.parse("2026-07-10T10:00:00Z")),
                        new MetricPoint(90, Map.of("host", "b"), t2)));

        String out = PrometheusExposition.format(List.of(a, b));

        assertThat(out.chars().filter(c -> c == '\n').count()).as("HELP + TYPE + 2 samples = 4 lines").isEqualTo(4L);
        // HELP/TYPE una sola vez
        assertThat(out.indexOf("# TYPE temp gauge")).isEqualTo(out.lastIndexOf("# TYPE temp gauge"));
        assertThat(out).contains("temp{host=\"a\"} 88.0");
        assertThat(out).contains("temp{host=\"b\"} 90.0");
    }

    @Test
    void emptySeriesProducesEmptyBody() {
        assertThat(PrometheusExposition.format(List.of())).isEqualTo("");
        assertThat(PrometheusExposition.format(null)).isEqualTo("");
    }

    @Test
    void contentTypeIsPrometheusExpositionV004() {
        assertThat(PrometheusExposition.CONTENT_TYPE).isEqualTo("text/plain; version=0.0.4; charset=utf-8");
    }
}
