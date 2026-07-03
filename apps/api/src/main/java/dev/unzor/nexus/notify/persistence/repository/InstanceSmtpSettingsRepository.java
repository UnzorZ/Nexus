package dev.unzor.nexus.notify.persistence.repository;

import dev.unzor.nexus.notify.domain.entity.InstanceSmtpSettings;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * Acceso a la fila singleton de SMTP de instancia ({@code id=1}). Lectura/escritura
 * mínima: {@link NotifyEmailSender} lee aquí al resolver el envío por defecto de un
 * proyecto; {@code InstanceSmtpSettingsService} (y el módulo {@code instance}) la
 * exponen/guardan desde el panel.
 */
public interface InstanceSmtpSettingsRepository extends Repository<InstanceSmtpSettings, Short> {

    Optional<InstanceSmtpSettings> findById(Short id);

    InstanceSmtpSettings save(InstanceSmtpSettings settings);
}
