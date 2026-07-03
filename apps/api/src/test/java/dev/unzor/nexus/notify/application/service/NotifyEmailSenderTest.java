package dev.unzor.nexus.notify.application.service;

import dev.unzor.nexus.notify.application.configuration.NotifySmtpProperties;
import dev.unzor.nexus.notify.domain.entity.InstanceSmtpSettings;
import dev.unzor.nexus.notify.domain.entity.ProjectSmtpSettings;
import dev.unzor.nexus.notify.persistence.repository.InstanceSmtpSettingsRepository;
import dev.unzor.nexus.notify.persistence.repository.ProjectSmtpSettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Orden de resolución del SMTP efectivo (ADR-0014): override del proyecto &rarr;
 * SMTP de instancia (DB) &rarr; env &rarr; sin configurar.
 */
class NotifyEmailSenderTest {

    private final ProjectSmtpSettingsRepository projectRepo = mock(ProjectSmtpSettingsRepository.class);
    private final InstanceSmtpSettingsRepository instanceRepo = mock(InstanceSmtpSettingsRepository.class);
    private final NotifyCrypto crypto = mock(NotifyCrypto.class);

    private NotifyEmailSender sender(NotifySmtpProperties env) {
        return new NotifyEmailSender(env, projectRepo, instanceRepo, crypto);
    }

    private static NotifySmtpProperties env(String host, String from) {
        return new NotifySmtpProperties(host, 587, "envuser", "envpass", from);
    }

    @Test
    void projectOverrideWins() {
        UUID pid = UUID.randomUUID();
        when(projectRepo.findByProjectId(pid)).thenReturn(Optional.of(
                new ProjectSmtpSettings(pid, "project.smtp", 2525, "puser", "p@from", "enc", "PUBLIC", null)));
        when(crypto.decrypt("enc")).thenReturn("secret");

        NotifyEmailSender.EffectiveSmtp smtp = sender(env("env.smtp", "env@from")).resolve(pid);

        assertThat(smtp.configured()).isTrue();
        assertThat(smtp.host()).isEqualTo("project.smtp");
        assertThat(smtp.from()).isEqualTo("p@from");
        assertThat(smtp.password()).isEqualTo("secret");
    }

    @Test
    void fallsBackToInstanceWhenNoProject() {
        UUID pid = UUID.randomUUID();
        when(projectRepo.findByProjectId(pid)).thenReturn(Optional.empty());
        when(instanceRepo.findById((short) 1)).thenReturn(Optional.of(
                new InstanceSmtpSettings("instance.smtp", 587, "iuser", "i@from", "enc", "PUBLIC", null, null)));
        when(crypto.decrypt("enc")).thenReturn("secret");

        NotifyEmailSender.EffectiveSmtp smtp = sender(env("env.smtp", "env@from")).resolve(pid);

        assertThat(smtp.configured()).isTrue();
        assertThat(smtp.host()).isEqualTo("instance.smtp");
        assertThat(smtp.from()).isEqualTo("i@from");
    }

    @Test
    void fallsBackToEnvWhenNeitherProjectNorInstance() {
        UUID pid = UUID.randomUUID();
        when(projectRepo.findByProjectId(pid)).thenReturn(Optional.empty());
        when(instanceRepo.findById((short) 1)).thenReturn(Optional.empty());

        NotifyEmailSender.EffectiveSmtp smtp = sender(env("env.smtp", "env@from")).resolve(pid);

        assertThat(smtp.configured()).isTrue();
        assertThat(smtp.host()).isEqualTo("env.smtp");
        assertThat(smtp.tlsMode().name()).isEqualTo("PUBLIC");
    }

    @Test
    void unconfiguredWhenNothingPresent() {
        UUID pid = UUID.randomUUID();
        when(projectRepo.findByProjectId(pid)).thenReturn(Optional.empty());
        when(instanceRepo.findById((short) 1)).thenReturn(Optional.empty());

        NotifyEmailSender.EffectiveSmtp smtp = sender(env(null, null)).resolve(pid);

        assertThat(smtp.configured()).isFalse();
    }

    @Test
    void instanceWithBlankHostDoesNotCountAsConfigured() {
        UUID pid = UUID.randomUUID();
        when(projectRepo.findByProjectId(pid)).thenReturn(Optional.empty());
        // Fila de instancia presente pero con host vacío (p. ej. guardada y luego
        // "limpiada"): no cuenta, cae al env.
        when(instanceRepo.findById((short) 1)).thenReturn(Optional.of(
                new InstanceSmtpSettings(null, null, null, null, null, "PUBLIC", null, null)));

        NotifyEmailSender.EffectiveSmtp smtp = sender(env("env.smtp", "env@from")).resolve(pid);

        assertThat(smtp.configured()).isTrue();
        assertThat(smtp.host()).isEqualTo("env.smtp");
    }
}
