package dev.unzor.nexus.apikeys.security;

import dev.unzor.nexus.apikeys.domain.entity.ProjectApiKey;
import dev.unzor.nexus.apikeys.domain.enums.ApiKeyStatus;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyDisabledException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyExpiredException;
import dev.unzor.nexus.apikeys.domain.exception.ApiKeyInvalidException;
import dev.unzor.nexus.apikeys.persistence.repository.ProjectApiKeyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Resuelve una API key cruda (cabecera {@code X-Nexus-Api-Key}) al proyecto al
 * que pertenece, verificando el hash en tiempo constante y el estado/expiración.
 *
 * <p>La búsqueda es por prefijo (global, puede devolver varias candidatas por
 * colisión de prefijo entre proyectos) y la confirmación por comparación
 * constant-time del hash, así nunca se fía del slug legible. En caso de éxito,
 * actualiza {@code last_used_at} (spec §21.1).</p>
 */
@Component
public class ApiKeyResolver {

    private final ProjectApiKeyRepository repository;
    private final ApiKeyHasher hasher;

    public ApiKeyResolver(ProjectApiKeyRepository repository, ApiKeyHasher hasher) {
        this.repository = repository;
        this.hasher = hasher;
    }

    @Transactional
    public ResolvedApiKey resolve(String rawKey) {
        String prefix = hasher.prefixOf(rawKey);
        if (prefix == null) {
            throw new ApiKeyInvalidException();
        }

        List<ProjectApiKey> candidates = repository.findByKeyPrefix(prefix);
        ProjectApiKey matched = candidates.stream()
                .filter(candidate -> hasher.verify(rawKey, candidate.getKeyHash()))
                .findFirst()
                .orElseThrow(ApiKeyInvalidException::new);

        if (matched.getStatus() == ApiKeyStatus.DISABLED) {
            throw new ApiKeyDisabledException(matched.getId(), matched.getProjectId());
        }
        if (matched.isExpired()) {
            throw new ApiKeyExpiredException(matched.getId(), matched.getProjectId());
        }
        matched.touchUsed();
        repository.save(matched);
        return new ResolvedApiKey(matched.getProjectId(), matched.getId(), matched.getKeyPrefix(), matched.getScopes());
    }
}
