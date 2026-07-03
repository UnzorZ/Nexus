package dev.unzor.nexus.notify.application.service;

import dev.unzor.nexus.notify.api.dto.SmtpSettingsSummary;
import dev.unzor.nexus.notify.domain.exception.InvalidNotificationRequestException;
import dev.unzor.nexus.notify.domain.exception.UnsafeSmtpHostException;
import dev.unzor.nexus.notify.persistence.repository.InstanceSmtpSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guardado de SMTP de instancia: anti-SSRF (loopback rechazado), CA exigida en
 * PINNED, resumen por defecto cuando no hay fila, y persistencia feliz. Usa IPs
 * literales (8.8.8.8 público / 127.0.0.1 loopback) para que {@link
 * dev.unzor.nexus.notify.application.service.SmtpHostGuard} no haga DNS.
 */
class InstanceSmtpSettingsServiceTest {

    private final InstanceSmtpSettingsRepository repository = mock(InstanceSmtpSettingsRepository.class);
    private final NotifyCrypto crypto = mock(NotifyCrypto.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private InstanceSmtpSettingsService service() {
        return new InstanceSmtpSettingsService(repository, crypto, publisher);
    }

    @Test
    void emptySummaryWhenNoRow() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        SmtpSettingsSummary summary = service().findSummary();
        assertThat(summary.passwordConfigured()).isFalse();
        assertThat(summary.tlsMode()).isEqualTo("PUBLIC");
        assertThat(summary.host()).isNull();
    }

    @Test
    void saveRejectsLoopbackHost() {
        assertThatThrownBy(() -> service().save("127.0.0.1", 587, "u", "f@from", "pw",
                "PUBLIC", null, UUID.randomUUID()))
                .isInstanceOf(UnsafeSmtpHostException.class);
    }

    @Test
    void pinnedModeRequiresCa() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().save("8.8.8.8", 587, "u", "f@from", "pw",
                "PINNED", null, UUID.randomUUID()))
                .isInstanceOf(InvalidNotificationRequestException.class);
    }

    @Test
    void savePersistsPublicHostAndReportsPasswordConfigured() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        when(crypto.encrypt("pw")).thenReturn("enc-pw");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SmtpSettingsSummary summary = service().save("8.8.8.8", 587, "u", "f@from", "pw",
                "PUBLIC", null, UUID.randomUUID());

        assertThat(summary.host()).isEqualTo("8.8.8.8");
        assertThat(summary.port()).isEqualTo(587);
        assertThat(summary.from()).isEqualTo("f@from");
        assertThat(summary.passwordConfigured()).isTrue();
        assertThat(summary.tlsMode()).isEqualTo("PUBLIC");
    }
}
