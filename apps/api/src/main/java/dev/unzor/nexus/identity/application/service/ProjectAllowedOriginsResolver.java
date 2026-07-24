package dev.unzor.nexus.identity.application.service;

import dev.unzor.nexus.identity.application.context.ProjectAuthenticationContext;
import dev.unzor.nexus.identity.domain.entity.ProjectOauthClient;
import dev.unzor.nexus.identity.domain.enums.OauthClientStatus;
import dev.unzor.nexus.identity.persistence.repository.ProjectOauthClientRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resuelve los orígenes web permitidos para el realm de un proyecto a partir de los
 * {@code redirectUris} (y {@code postLogoutRedirectUis}) de sus clientes OAuth
 * <b>ACTIVOS</b>. Es la base del CORS dinámico de {@code /api/p/**} y del redirect
 * del entry point de login embebido: un proyecto autoriza sus propios orígenes al
 * registrarlos como redirect URIs, sin que el operador toque la configuración de la
 * instancia.
 *
 * <p>El realm se resuelve con {@link ProjectSlugResolver#resolveExisting(String)} —el
 * mismo camino que usa {@code ProjectRealmIsolationFilter}— de forma que el aislamiento
 * entre realms se conserva incluso para proyectos no operativos. Los clientes
 * {@link OauthClientStatus#DISABLED} se excluyen, igual que hace
 * {@code CompositeRegisteredClientRepository.isActive}.</p>
 */
@Service
public class ProjectAllowedOriginsResolver {

    private final ProjectSlugResolver slugResolver;
    private final ProjectOauthClientRepository clientRepository;
    private final OriginCache cache = new OriginCache();

    public ProjectAllowedOriginsResolver(
            ProjectSlugResolver slugResolver,
            ProjectOauthClientRepository clientRepository
    ) {
        this.slugResolver = slugResolver;
        this.clientRepository = clientRepository;
    }

    /**
     * Orígenes permitidos para el realm {@code {slug}}. Conjunto vacío si el slug no
     * resuelve a un proyecto o éste no tiene clientes activos con redirect URIs válidas.
     * Nunca {@code null}.
     */
    public Set<String> originsForSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Set.of();
        }
        Set<String> cached = cache.get(slug);
        if (cached != null) {
            return cached;
        }
        Set<String> origins = computeOrigins(slug);
        cache.put(slug, origins);
        return origins;
    }

    /**
     * Origen web "principal" del proyecto (para construir enlaces públicos —p. ej. el de
     * verificación de email— que deben aterrizar en el dominio de la <b>app</b>, no en el
     * frontend propio de Nexus). Es el primero alfabéticamente entre los orígenes derivados
     * de los redirect URIs de los clientes activos del proyecto (determinista; la mayoría de
     * apps self-service tienen un único origen). Vacío si el proyecto no expone ningún
     * origen registrado —el llamador cae entonces al {@code frontend-base-url} de la
     * instancia (portal hosteado por Nexus).
     */
    public Optional<String> primaryProjectOrigin(String slug) {
        return originsForSlug(slug).stream().sorted().findFirst();
    }

    private Set<String> computeOrigins(String slug) {
        UUID projectId;
        try {
            ProjectAuthenticationContext realm = slugResolver.resolveExisting(slug);
            projectId = realm.projectId();
        } catch (RuntimeException notFound) {
            // Slug inexistente o proyecto borrado: sin orígenes (denegar por defecto).
            // El handler del realm devolverá su 404 habitual; aquí sólo decidimos CORS.
            return Set.of();
        }
        List<ProjectOauthClient> clients = clientRepository.findAllByProjectId(projectId);
        Set<String> origins = new HashSet<>();
        for (ProjectOauthClient client : clients) {
            if (client.getStatus() != OauthClientStatus.ACTIVE) {
                continue;
            }
            addOrigins(origins, client.getRedirectUris());
            addOrigins(origins, client.getPostLogoutRedirectUris());
        }
        return Set.copyOf(origins);
    }

    private static void addOrigins(Set<String> origins, List<String> uris) {
        if (uris == null) {
            return;
        }
        for (String uri : uris) {
            String origin = WebOrigin.of(uri);
            if (origin != null) {
                origins.add(origin);
            }
        }
    }

    /**
     * Caché TTL mínimo (sin Caffeine en el classpath): evita repetir las queries de
     * realm+clientes en cada par preflight+petición. Caducidad perezosa; capped para
     * no crecer sin límite. Los cambios en redirect URIs tardan a lo sumo {@code TTL}
     * en reflejarse (cambio raro; aceptable para CORS de login).
     */
    private static final class OriginCache {
        private static final long TTL_MS = 30_000L;
        private static final int MAX_ENTRIES = 1_024;

        private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

        Set<String> get(String slug) {
            Entry entry = store.get(slug);
            if (entry == null || System.currentTimeMillis() - entry.timestamp() > TTL_MS) {
                return null;
            }
            return entry.origins();
        }

        void put(String slug, Set<String> origins) {
            if (store.size() > MAX_ENTRIES) {
                store.clear();
            }
            store.put(slug, new Entry(origins, System.currentTimeMillis()));
        }

        private record Entry(Set<String> origins, long timestamp) {
        }
    }
}
