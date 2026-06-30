package dev.unzor.nexus.modules.gate;

import dev.unzor.nexus.modules.application.service.ProjectModuleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Gate cross-cutting de módulos (spec §16.1, §449/§706): para toda petición cuyo
 * módulo esté deshabilitado para el proyecto, responde {@code 403 module_disabled}
 * antes de alcanzar el controlador. Resuelve {@code (projectId, módulo)} vía
 * {@link ModuleGate} y consulta {@link ProjectModuleService#isEnabled}.
 *
 * <p>Es ortogonal al RBAC: no comprueba membresía ni {@code ROLE_INSTANCE_ADMIN} —
 * un módulo apagado lo está para todos. Los endpoints de gestión
 * ({@code /modules}) y los que no tienen módulo gateable (members, api-keys,
 * settings…) resuelven vacío y pasan sin tocar la base.</p>
 */
@Component
public class ModuleGateInterceptor implements HandlerInterceptor {

    private final ModuleGate gate;
    private final ProjectModuleService moduleService;
    private final ModuleGateProblemWriter problemWriter;

    public ModuleGateInterceptor(ModuleGate gate, ProjectModuleService moduleService, ModuleGateProblemWriter problemWriter) {
        this.gate = gate;
        this.moduleService = moduleService;
        this.problemWriter = problemWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        return gate.resolve(request).map(gated -> {
            if (moduleService.isEnabled(gated.projectId(), gated.module())) {
                return true;
            }
            problemWriter.writeDisabled(response, gated.module());
            return false;
        }).orElse(true);
    }
}
