package dev.unzor.nexus.apikeys.api.controller;

import dev.unzor.nexus.apikeys.api.RequiredScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller de prueba (solo tests): un endpoint con scope requerido (rama
 * {@code 403 missing_scope}) y otro <b>sin anotar</b> (rama {@code 403 scope_required}
 * del régimen deny-by-default). Se importa explícitamente en
 * {@link ProjectApiRuntimeTests}.
 */
@RestController
@RequestMapping("/api/v1/test")
class TestScopedController {

    @GetMapping("/scoped")
    @RequiredScope("test:scoped")
    Map<String, Object> scoped() {
        return Map.of("ok", true);
    }

    /** Sin @RequiredScope ni @ScopeFree: debe caer en el deny-by-default. */
    @GetMapping("/unscoped")
    Map<String, Object> unscoped() {
        return Map.of("ok", true);
    }
}
