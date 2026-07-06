package dev.unzor.nexus.instance.persistence.repository;

import dev.unzor.nexus.instance.domain.entity.InstanceSettings;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/** Fila singleton de configuración de instancia ({@code id=1}). */
public interface InstanceSettingsRepository extends Repository<InstanceSettings, Short> {

    Optional<InstanceSettings> findById(Short id);

    InstanceSettings save(InstanceSettings settings);
}
