package dev.unzor.nexus.modules.gate;

import dev.unzor.nexus.apikeys.security.ResolvedApiKey;
import dev.unzor.nexus.modules.application.service.ProjectModuleService;
import dev.unzor.nexus.projects.application.service.ProjectAccessService;
import dev.unzor.nexus.shared.security.AuthenticatedAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Gate cross-cutting de módulos (spec §16.1, §449/§706): para toda petición cuyo
 * módulo esté deshabilitado para el proyecto, responde {@code 403 module_disabled}
 * antes de alcanzar el controlador. Resuelve {@code (projectId, módulo)} vía
 * {@link ModuleGate} y consulta {@link ProjectModuleService#isEnabled}.
 *
 * <p>Es ortogonal al RBAC: no sortea el gate ni siquiera {@code ROLE_INSTANCE_ADMIN}
 * — un módulo apagado lo está para todos. Los endpoints de gestión
 * ({@code /modules}) y los que no tienen módulo gateable (members, api-keys,
 * settings…) resuelven vacío y pasan sin tocar la base.</p>
 *
 * <p><b>Filtrado de estado sin autorización:</b> antes de aplicar el gate se
 * comprueba el acceso del caller. Quien no esté autorizado recibe el
 * {@code permission_denied} habitual del controlador (la petición pasa sin tocar el
 * gate), de modo que un no-miembro no pueda distinguir {@code module_disabled} de
 * {@code permission_denied} y así inferir el estado del módulo o confirmar el
 * projectId. En runtime la API key ya está ligada al proyecto (la resolvió el
 * filtro), así que se considera autorizado de entrada.</p>
 */
@Component
public class ModuleGateInterceptor implements HandlerInterceptor {

    private static final String INSTANCE_ADMIN_AUTHORITY = "ROLE_INSTANCE_ADMIN";

    private final ModuleGate gate;
    private final ProjectModuleService moduleService;
    private final ProjectAccessService accessService;
    private final ModuleGateProblemWriter problemWriter;

    public ModuleGateInterceptor(
            ModuleGate gate,
            ProjectModuleService moduleService,
            ProjectAccessService accessService,
            ModuleGateProblemWriter problemWriter
    ) {
        this.gate = gate;
        this.moduleService = moduleService;
        this.accessService = accessService;
        this.problemWriter = problemWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        return gate.resolve(request).map(gated -> {
            if (!isCallerAuthorized(gated)) {
                // Sin acceso: deja que el controlador responda su permission_denied.
                // No revelamos module_disabled a quienes no están autorizados.
                return true;
            }
            if (moduleService.isEnabled(gated.projectId(), gated.module())) {
                return true;
            }
            problemWriter.writeDisabled(response, gated.module());
            return false;
        }).orElse(true);
    }

    private boolean isCallerAuthorized(ModuleGate.GatedRequest gated) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        // Runtime: la API key está ligada al proyecto (la resolvió el filtro) → autorizado.
        if (principal instanceof ResolvedApiKey) {
            return true;
        }
        // Panel: membresía activa o instance admin.
        if (principal instanceof AuthenticatedAccount account) {
            return accessService.canAccess(gated.projectId(), account.accountId(), isInstanceAdmin(authentication));
        }
        return false;
    }

    private static boolean isInstanceAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(INSTANCE_ADMIN_AUTHORITY));
    }
}
