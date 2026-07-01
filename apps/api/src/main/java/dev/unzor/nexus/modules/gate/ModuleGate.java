package dev.unzor.nexus.modules.gate;

import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import dev.unzor.nexus.modules.domain.enums.NexusModule;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resuelve, a partir de una petición, el par {@code (projectId, módulo)} que el
 * {@link ModuleGateInterceptor} debe comprobar — o {@link Optional#empty()} si la
 * petición no pertenece a ningún módulo gateable.
 *
 * <p>Dos superficies con mecánicas distintas de resolución del projectId:</p>
 * <ul>
 *   <li><b>Panel</b> {@code /api/panel/v1/projects/{projectId}/{segment}}: el
 *       projectId viene del path; el módulo, del primer segmento tras el
 *       projectId (mapa {@link #PANEL_SEGMENTS}).</li>
 *   <li><b>Runtime</b> {@code /api/v1/**}: el projectId viene del principal
 *       {@link ResolvedApiKey} (resuelto por el filtro de API key); el módulo,
 *       del prefijo del path (mapa {@link #RUNTIME_PREFIXES}).</li>
 * </ul>
 *
 * <p>Quedan <em>fuera</em> del gate (ausentes en los mapas): la gestión de
 * módulos ({@code /modules}, para no bloquear el re-encendido), members, api-keys,
 * settings raíz, {@code /whoami}, sesión/cuenta, {@code /internal} y OAuth.</p>
 *
 * <p>El núcleo {@link #resolve(String, Object)} es puro y unit-testeable; el
 * {@link #resolve(HttpServletRequest)} de envoltorio sólo extrae el URI y el
 * principal del contexto de seguridad.</p>
 */
@Component
public class ModuleGate {

    private static final Pattern PANEL_PATTERN = Pattern.compile(
            "^/api/panel/v1/projects/(?<projectId>[^/]+)(?:/(?<segment>[^/]+))?");

    /** Segmento del panel (tras {projectId}) → módulo. Ausente o no listado = no gateado. */
    private static final Map<String, NexusModule> PANEL_SEGMENTS = Map.of(
            "permissions", NexusModule.PERMISSIONS,
            "roles", NexusModule.PERMISSIONS,
            "audit", NexusModule.AUDIT,
            "heartbeats", NexusModule.REGISTRY);

    /** Prefijo del runtime → módulo. No listado (p. ej. /whoami) = no gateado. */
    private static final Map<String, NexusModule> RUNTIME_PREFIXES = Map.of(
            "/api/v1/registry", NexusModule.REGISTRY);

    /** Petición que el gate debe evaluar: proyecto + módulo a comprobar. */
    public record GatedRequest(UUID projectId, NexusModule module) {
    }

    public Optional<GatedRequest> resolve(HttpServletRequest request) {
        return resolve(request.getRequestURI(), currentPrincipal());
    }

    /**
     * Núcleo puro: dado el URI de la petición y el principal resuelto, decide qué
     * módulo (si alguno) gatear. Devuelve vacío si la ruta no es gateable o si
     * falta el projectId (path no parseable o runtime sin API key resuelta — en
     * cuyo caso el filtro de autenticación ya habrá rechazado).
     */
    Optional<GatedRequest> resolve(String requestUri, Object principal) {
        if (requestUri == null) {
            return Optional.empty();
        }

        Matcher panel = PANEL_PATTERN.matcher(requestUri);
        if (panel.find()) {
            UUID projectId = tryParseUuid(panel.group("projectId"));
            String segment = panel.group("segment"); // null cuando no hay segmento (settings raíz)
            NexusModule module = segment == null ? null : PANEL_SEGMENTS.get(segment);
            if (projectId != null && module != null) {
                return Optional.of(new GatedRequest(projectId, module));
            }
            return Optional.empty();
        }

        for (Map.Entry<String, NexusModule> entry : RUNTIME_PREFIXES.entrySet()) {
            if (requestUri.startsWith(entry.getKey())) {
                if (principal instanceof ResolvedApiKey key) {
                    return Optional.of(new GatedRequest(key.projectId(), entry.getValue()));
                }
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static Object currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getPrincipal();
    }

    private static UUID tryParseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
