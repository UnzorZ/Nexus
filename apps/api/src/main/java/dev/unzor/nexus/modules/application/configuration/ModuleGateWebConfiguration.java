package dev.unzor.nexus.modules.application.configuration;

import dev.unzor.nexus.modules.gate.ModuleGateInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra el {@link ModuleGateInterceptor} sobre las dos superficies gateables:
 * el panel de proyecto ({@code /api/panel/v1/projects/**}, projectId del path) y
 * el API de runtime ({@code /api/v1/**}, projectId del principal). El interceptor
 * decide (vía {@code ModuleGate}) qué rutas pertenecen a un módulo y deja pasar el
 * resto sin consultar la base.
 */
@Configuration
public class ModuleGateWebConfiguration implements WebMvcConfigurer {

    private final ModuleGateInterceptor moduleGateInterceptor;

    public ModuleGateWebConfiguration(ModuleGateInterceptor moduleGateInterceptor) {
        this.moduleGateInterceptor = moduleGateInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(moduleGateInterceptor)
                .addPathPatterns("/api/panel/v1/projects/**", "/api/v1/**");
    }
}
