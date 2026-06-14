package dev.unzor.nexus.admin.application.events;

import dev.unzor.nexus.admin.domain.events.NexusAccountSessionsRevocationRequested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PanelSessionRevocationRepublisher}.
 *
 * <p>Cover the {@link ResubmissionOptions} it builds: the filter only accepts
 * revocation events, the limits reflect configuration, startup uses {@code minAge} zero
 * and the periodic run uses the configured {@code minAge}.
 */
class PanelSessionRevocationRepublisherTest {

    @Test
    void filterAcceptsOnlyRevocationEvents() {
        PanelSessionRevocationProperties properties = new PanelSessionRevocationProperties(
                100, 10, Duration.ofSeconds(15));
        PanelSessionRevocationRepublisher republisher = newRepublisher(properties);

        ResubmissionOptions options = republisher.options(Duration.ZERO);
        var filter = options.getFilter();

        EventPublication revocation = publication(new NexusAccountSessionsRevocationRequested(UUID.randomUUID()));
        EventPublication other = publication("some-other-event");

        assertThat(filter.test(revocation)).isTrue();
        assertThat(filter.test(other)).isFalse();
    }

    @Test
    void optionsContainConfiguredLimits() {
        PanelSessionRevocationProperties properties = new PanelSessionRevocationProperties(
                50, 5, Duration.ofSeconds(30));
        PanelSessionRevocationRepublisher republisher = newRepublisher(properties);

        ResubmissionOptions options = republisher.options(Duration.ofSeconds(30));

        assertThat(options.getBatchSize()).isEqualTo(50);
        assertThat(options.getMaxInFlight()).isEqualTo(5);
        assertThat(options.getMinAge()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void startupUsesZeroMinAge() {
        PanelSessionRevocationProperties properties = new PanelSessionRevocationProperties(
                100, 10, Duration.ofSeconds(15));
        IncompleteEventPublications incomplete = Mockito.mock(IncompleteEventPublications.class);
        PanelSessionRevocationRepublisher republisher =
                new PanelSessionRevocationRepublisher(incomplete, properties);

        republisher.resubmitOnStartup();

        ResubmissionOptions options = captureOptions(incomplete);
        assertThat(options.getMinAge()).isEqualTo(Duration.ZERO);
        assertThat(options.getBatchSize()).isEqualTo(100);
        assertThat(options.getMaxInFlight()).isEqualTo(10);
    }

    @Test
    void periodicUsesConfiguredMinAge() {
        PanelSessionRevocationProperties properties = new PanelSessionRevocationProperties(
                100, 10, Duration.ofSeconds(15));
        IncompleteEventPublications incomplete = Mockito.mock(IncompleteEventPublications.class);
        PanelSessionRevocationRepublisher republisher =
                new PanelSessionRevocationRepublisher(incomplete, properties);

        republisher.resubmitPeriodically();

        ResubmissionOptions options = captureOptions(incomplete);
        assertThat(options.getMinAge()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void propertiesRejectNegativeMinAge() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new PanelSessionRevocationProperties(100, 10, Duration.ofSeconds(-1)));
    }

    private static PanelSessionRevocationRepublisher newRepublisher(PanelSessionRevocationProperties properties) {
        return new PanelSessionRevocationRepublisher(Mockito.mock(IncompleteEventPublications.class), properties);
    }

    private static EventPublication publication(Object event) {
        EventPublication mock = Mockito.mock(EventPublication.class);
        when(mock.getEvent()).thenReturn(event);
        return mock;
    }

    private static ResubmissionOptions captureOptions(IncompleteEventPublications incomplete) {
        ArgumentCaptor<ResubmissionOptions> captor = ArgumentCaptor.forClass(ResubmissionOptions.class);
        verify(incomplete).resubmitIncompletePublications(captor.capture());
        return captor.getValue();
    }
}
